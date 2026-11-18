package edu.eci.dosw.campusseguro.training.progress;

import edu.eci.dosw.campusseguro.training.content.Content;
import edu.eci.dosw.campusseguro.training.content.ContentService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

// RF-13: registers that a user opened a content. Rule 2: one row per user and content; a new view only
// moves the date of the last view.
@Service
public class ContentViewService {

    private final ContentViewRepository repository;
    private final ContentService contentService;
    private final TransactionTemplate transaction;

    public ContentViewService(ContentViewRepository repository, ContentService contentService,
                              PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.contentService = contentService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Result(ContentViewResponse view, boolean created) {
    }

    // Updates the existing row first; if there is none, inserts it. When two first views arrive at once,
    // the unique index rejects the second insert and that request updates the row the other one created.
    public Result register(UUID userId, UUID contentId) {
        Content content = contentService.requireActive(contentId);
        Instant now = Instant.now();
        if (touch(userId, contentId, now)) {
            return new Result(find(userId, contentId), false);
        }
        try {
            transaction.executeWithoutResult(status -> repository.saveAndFlush(new ContentView(userId, content, now)));
            return new Result(find(userId, contentId), true);
        } catch (DataIntegrityViolationException e) {
            touch(userId, contentId, now);
            return new Result(find(userId, contentId), false);
        }
    }

    private boolean touch(UUID userId, UUID contentId, Instant now) {
        Integer updated = transaction.execute(status -> repository.touch(userId, contentId, now));
        return updated != null && updated > 0;
    }

    private ContentViewResponse find(UUID userId, UUID contentId) {
        return transaction.execute(status -> repository.findByUserIdAndContentId(userId, contentId)
                .map(ContentViewResponse::from)
                .orElseThrow());
    }
}
