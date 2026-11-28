package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.common.Status;
import edu.eci.dosw.campusseguro.training.content.Content;
import edu.eci.dosw.campusseguro.training.content.ContentChangedEvent;
import edu.eci.dosw.campusseguro.training.content.ContentRepository;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapter;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

// Embeddings of the active contents, for the search by meaning of the tutor (RF-15). They are stored in
// content_embedding and kept in memory, so a question only waits for the embedding of the question itself.
// They are calculated when MS07 starts (the missing or outdated ones) and after a content is created, edited or
// reactivated, always in the background. If the provider has no embeddings or fails, the search uses only words.
@Service
public class ContentEmbeddings {

    static final int BATCH = 32;
    static final int MAX_TEXT = 6000;

    private static final Logger log = LoggerFactory.getLogger(ContentEmbeddings.class);

    private final AiAdapters adapters;
    private final ContentRepository contents;
    private final ContentEmbeddingRepository embeddings;
    private final Executor executor;
    private final Map<UUID, float[]> vectors = new ConcurrentHashMap<>();

    public ContentEmbeddings(AiAdapters adapters, ContentRepository contents, ContentEmbeddingRepository embeddings,
                             @Qualifier(TutorSearchConfig.EMBEDDING_EXECUTOR) Executor executor) {
        this.adapters = adapters;
        this.contents = contents;
        this.embeddings = embeddings;
        this.executor = executor;
    }

    public boolean available() {
        return adapters.current().supportsEmbeddings();
    }

    // Vector of the question, which must already be redacted. Empty if embeddings are off or the provider fails.
    public Optional<float[]> embedQuestion(String redactedQuestion) {
        if (!available()) {
            return Optional.empty();
        }
        long start = System.nanoTime();
        try {
            float[] vector = adapters.current().embed(List.of(redactedQuestion)).get(0);
            log.debug("Question embedding in {} ms", (System.nanoTime() - start) / 1_000_000);
            return Optional.of(vector);
        } catch (RuntimeException e) {
            log.warn("Question embedding not available, searching by words only: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    // Vector of a content, if it has one.
    public Optional<float[]> vectorOf(UUID contentId) {
        return Optional.ofNullable(vectors.get(contentId));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (available()) {
            executor.execute(this::refreshAll);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onContentChanged(ContentChangedEvent event) {
        if (available()) {
            executor.execute(() -> refresh(List.of(event.contentId())));
        }
    }

    // Loads the stored vectors and calculates the missing or outdated ones of every active content.
    public void refreshAll() {
        if (!available()) {
            return;
        }
        List<UUID> active = contents.findByStatus(Status.ACTIVO, Pageable.unpaged()).getContent().stream()
                .map(Content::getId)
                .toList();
        refresh(active);
    }

    void refresh(List<UUID> contentIds) {
        AiAdapter adapter = adapters.current();
        String model = adapter.embeddingModel();
        Map<UUID, ContentEmbedding> stored = embeddings.findByModel(model).stream()
                .collect(Collectors.toMap(ContentEmbedding::getContentId, Function.identity()));
        List<Content> pending = new ArrayList<>();
        for (UUID id : contentIds) {
            Content content = contents.findById(id).orElse(null);
            if (content == null || !content.isActive()) {
                continue;
            }
            ContentEmbedding current = stored.get(id);
            if (current != null && current.getContentUpdatedAt().equals(content.getUpdatedAt())) {
                vectors.put(id, current.vector());
            } else {
                pending.add(content);
            }
        }
        for (int from = 0; from < pending.size(); from += BATCH) {
            List<Content> batch = pending.subList(from, Math.min(from + BATCH, pending.size()));
            try {
                List<float[]> calculated = adapter.embed(batch.stream().map(ContentEmbeddings::textOf).toList());
                for (int i = 0; i < batch.size(); i++) {
                    Content content = batch.get(i);
                    embeddings.save(new ContentEmbedding(content.getId(), model, calculated.get(i), content.getUpdatedAt()));
                    vectors.put(content.getId(), calculated.get(i));
                }
            } catch (RuntimeException e) {
                log.warn("Content embeddings could not be calculated: {}", e.getClass().getSimpleName());
                return;
            }
        }
        if (!pending.isEmpty()) {
            log.info("Calculated {} content embeddings", pending.size());
        }
    }

    static String textOf(Content content) {
        String text = content.getTitle() + "\n" + content.getBody();
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }

    static double cosine(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
