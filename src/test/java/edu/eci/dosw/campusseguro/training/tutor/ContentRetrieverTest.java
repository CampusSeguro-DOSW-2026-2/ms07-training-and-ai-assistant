package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryRepository;
import edu.eci.dosw.campusseguro.training.content.Content;
import edu.eci.dosw.campusseguro.training.content.ContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// RF-15 step 4: which active contents the tutor can use for a question.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContentRetrieverTest {

    @Autowired
    private ContentRetriever retriever;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ContentRepository contents;

    private Category category;

    @BeforeEach
    void setUp() {
        category = categories.save(new Category("Test Tutor", null));
    }

    private Content content(String title, String body) {
        return contents.save(new Content(title, body, category));
    }

    private List<String> titles(String question) {
        return retriever.find(question).stream().map(RelatedContent::title).toList();
    }

    @Test
    void findsTheContentAboutTheQuestionAndPutsTitleMatchesFirst() {
        content("Qué hacer durante un sismo", "Agáchate, cúbrete y sujétate lejos de ventanas.");
        content("Simulacros en el campus", "En los simulacros de sismo cada clase practica la evacuación.");
        content("Uso del extintor", "Hala el pasador y apunta a la base del fuego.");

        assertThat(titles("¿Qué hago si hay un sismo en clase?"))
                .containsExactly("Qué hacer durante un sismo", "Simulacros en el campus");
    }

    @Test
    void ignoresAccentsCaseAndPlurals() {
        content("Evacuación de laboratorios", "Cierra el gas y sal por la ruta señalizada.");

        assertThat(titles("¿Cómo EVACUAR los LABORATORIOS?")).containsExactly("Evacuación de laboratorios");
    }

    @Test
    void unrelatedQuestionFindsNothing() {
        content("Qué hacer durante un sismo", "Agáchate, cúbrete y sujétate.");

        assertThat(retriever.find("¿Me das una receta de pasta con queso?")).isEmpty();
    }

    @Test
    void questionWithOnlyStopWordsFindsNothing() {
        content("Qué hacer durante un sismo", "Agáchate, cúbrete y sujétate.");

        assertThat(retriever.find("¿Qué hago? Dime por favor")).isEmpty();
    }

    @Test
    void aSingleWordInTheBodyIsNotEnough() {
        content("Uso del extintor", "Revisa también la ruta de evacuación del edificio.");

        assertThat(retriever.find("¿Dónde queda el edificio?")).isEmpty();
    }

    @Test
    void inactiveContentIsNeverUsed() {
        Content old = content("Sismo: protocolo anterior", "Texto retirado.");
        old.deactivate();
        contents.flush();

        assertThat(retriever.find("protocolo de sismo")).isEmpty();
    }

    @Test
    void activeContentOfAnInactiveCategoryIsUsed() {
        content("Sismo en auditorios", "Quédate en tu silla y protege tu cabeza.");
        category.deactivate();
        categories.flush();

        assertThat(titles("sismo en el auditorio")).containsExactly("Sismo en auditorios");
    }

    @Test
    void returnsAtMostThreeContents() {
        for (int i = 1; i <= 5; i++) {
            content("Sismo " + i, "Recomendación " + i);
        }

        assertThat(retriever.find("sismo")).hasSize(ContentRetriever.MAX_RESULTS);
    }

    @Test
    void longBodiesAreCut() {
        content("Sismo y evacuación", "a".repeat(ContentRetriever.MAX_TEXT_LENGTH + 500));

        RelatedContent related = retriever.find("evacuación por sismo").get(0);

        assertThat(related.text()).hasSize(ContentRetriever.MAX_TEXT_LENGTH + 1).endsWith("…");
    }

    @Test
    void synonymsFindTheContent() {
        content("Qué hacer durante un sismo", "Agáchate, cúbrete y sujétate.");

        assertThat(titles("Hola, ¿qué hago si hay un terremoto?")).containsExactly("Qué hacer durante un sismo");
        assertThat(titles("¿y si tiembla muy fuerte?")).containsExactly("Qué hacer durante un sismo");
    }

    @Test
    void typosFindTheContent() {
        content("Evacuación de laboratorios", "Cierra el gas y sal por la ruta señalizada.");
        content("Qué hacer durante un sismo", "Agáchate, cúbrete y sujétate.");

        assertThat(titles("como evacuo el laboratrio")).containsExactly("Evacuación de laboratorios");
        assertThat(titles("que hago en un sizmo")).containsExactly("Qué hacer durante un sismo");
    }

    @Test
    void specificWordsWeighMoreThanCommonOnes() {
        content("Qué hacer durante un sismo en espacios abiertos", "Aléjate de postes y cables.");
        content("Qué hacer durante un sismo en espacios cerrados", "Cúbrete debajo de una mesa.");
        content("Primeras acciones después de un sismo", "Revisa si hay lesiones.");
        content("Qué hacer en un salón de clase", "Protégete debajo de tu mesa en el salón.");

        assertThat(titles("estoy en el aula y tiembla").get(0)).isEqualTo("Qué hacer en un salón de clase");
    }

    @Test
    void stemsJoinWordsOfTheSameFamily() {
        assertThat(ContentRetriever.stems("¿Qué hago en los sismos y la evacuación?"))
                .containsExactly("sismo", "evacu");
    }
}
