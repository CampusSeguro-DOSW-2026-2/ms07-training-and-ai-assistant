package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryRepository;
import edu.eci.dosw.campusseguro.training.content.Content;
import edu.eci.dosw.campusseguro.training.content.ContentRepository;
import edu.eci.dosw.campusseguro.training.exception.DependencyUnavailableException;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapter;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapters;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiPrompt;
import edu.eci.dosw.campusseguro.training.tutor.ai.TutorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The search by meaning with a fake model that knows a few concepts: it can relate texts that share no words.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TutorSemanticSearchTest {

    // Each concept is one dimension; a text points to the concepts of the words it has.
    static class ConceptEmbeddings implements AiAdapter {
        static final Map<String, Integer> CONCEPTS = Map.of(
                "ascensor", 0, "atrapado", 0, "encerrado", 0, "cabina", 0,
                "receta", 1, "pasta", 1,
                "sismo", 2, "temblor", 2);

        final List<String> received = new ArrayList<>();
        volatile boolean failing;
        int calls;

        @Override
        public String provider() {
            return "concepts";
        }

        @Override
        public String complete(AiPrompt prompt) {
            return "Respuesta de prueba.";
        }

        @Override
        public boolean supportsEmbeddings() {
            return true;
        }

        @Override
        public String embeddingModel() {
            return "fake-concepts";
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            calls++;
            received.addAll(texts);
            if (failing) {
                throw new DependencyUnavailableException("down");
            }
            return texts.stream().map(ConceptEmbeddings::vector).toList();
        }

        static float[] vector(String text) {
            float[] vector = new float[4];
            vector[3] = 0.05f;
            for (String word : InputAnalyzer.normalize(text).split(" ")) {
                Integer concept = CONCEPTS.get(word);
                if (concept != null) {
                    vector[concept] += 1;
                }
            }
            return vector;
        }
    }

    @TestConfiguration
    static class ConceptConfig {

        @Bean
        ConceptEmbeddings conceptEmbeddings() {
            return new ConceptEmbeddings();
        }

        @Bean
        @Primary
        AiAdapters conceptAdapters(ConceptEmbeddings fake) {
            return new AiAdapters(List.of(fake), new TutorProperties("concepts", null, null, null, null, null, null));
        }
    }

    @Autowired
    private ConceptEmbeddings fake;

    @Autowired
    private ContentRetriever retriever;

    @Autowired
    private ContentEmbeddings embeddings;

    @Autowired
    private ContentEmbeddingRepository stored;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ContentRepository contents;

    @Autowired
    private TutorService tutor;

    private Content elevator;
    private Content earthquake;

    @BeforeEach
    void setUp() {
        fake.failing = false;
        fake.received.clear();
        fake.calls = 0;
        Category category = categories.save(new Category("Test Significado", null));
        elevator = contents.save(new Content("Si quedas atrapado en un ascensor",
                "Mantén la calma, presiona el botón de alarma y espera la ayuda.", category));
        earthquake = contents.save(new Content("Qué hacer durante un sismo", "Agáchate, cúbrete y sujétate.", category));
        contents.flush();
        embeddings.refreshAll();
    }

    private List<String> titles(String question) {
        float[] vector = embeddings.embedQuestion(question).orElse(null);
        return retriever.find(question, vector).stream().map(RelatedContent::title).toList();
    }

    @Test
    void findsAContentByMeaningWithoutSharedWords() {
        assertThat(retriever.find("me quedé encerrado en la cabina")).isEmpty();

        assertThat(titles("me quedé encerrado en la cabina")).containsExactly(elevator.getTitle());
    }

    @Test
    void meaningDoesNotBringUnrelatedContents() {
        assertThat(titles("receta de pasta")).isEmpty();
    }

    @Test
    void meaningNeverHidesWhatTheWordsFound() {
        assertThat(titles("qué hago en un sismo")).containsExactly(earthquake.getTitle());
    }

    @Test
    void eachActiveContentIsStoredOnceAndOnlyRecalculatedWhenItChanges() {
        assertThat(stored.findById(elevator.getId())).get()
                .extracting(ContentEmbedding::getModel).isEqualTo("fake-concepts");
        int calls = fake.calls;

        embeddings.refreshAll();
        assertThat(fake.calls).isEqualTo(calls);

        elevator.update("Si quedas atrapado en un ascensor o elevador", elevator.getBody(), elevator.getCategory());
        contents.flush();
        embeddings.refreshAll();
        assertThat(fake.calls).isEqualTo(calls + 1);
        assertThat(fake.received.get(fake.received.size() - 1)).startsWith("Si quedas atrapado en un ascensor o elevador");
    }

    @Test
    void inactiveContentsHaveNoEmbedding() {
        Content old = contents.save(new Content("Protocolo retirado", "Texto viejo.", earthquake.getCategory()));
        old.deactivate();
        contents.flush();

        embeddings.refreshAll();

        assertThat(stored.findById(old.getId())).isEmpty();
    }

    @Test
    void whenEmbeddingsFailTheSearchUsesOnlyWords() {
        fake.failing = true;

        assertThat(embeddings.embedQuestion("qué hago en un sismo")).isEmpty();
        assertThat(titles("qué hago en un sismo")).containsExactly(earthquake.getTitle());
        assertThat(titles("me quedé encerrado en la cabina")).isEmpty();
    }

    @Test
    void theQuestionIsEmbeddedAlreadyRedacted() {
        fake.received.clear();

        tutor.ask(UUID.randomUUID(), "Me llamo Ana Gómez, correo ana@gmail.com, quedé encerrado en la cabina");

        assertThat(fake.received).hasSize(1);
        assertThat(fake.received.get(0)).contains("[nombre]", "[correo]").doesNotContain("Ana Gómez", "ana@gmail.com");
    }

    @Test
    void vectorsAreStoredAsJson() {
        float[] vector = {0.5f, -0.25f, 1.0e-4f};

        assertThat(ContentEmbedding.fromJson(ContentEmbedding.toJson(vector))).containsExactly(vector);
        assertThat(ContentEmbeddings.cosine(vector, vector)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(ContentEmbeddings.cosine(new float[]{1, 0}, new float[]{0, 1})).isZero();
    }
}
