package edu.eci.dosw.campusseguro.training.tutor;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Synonyms and typo tolerance of the tutor search.
class TutorTermsTest {

    private static final Set<String> VOCABULARY = Set.of("sismo", "evacuacion", "laboratorio", "auditorio", "salon");

    @Test
    void synonymsBecomeTheWordOfTheContents() {
        assertThat(TutorTerms.canonical("terremoto")).isEqualTo("sismo");
        assertThat(TutorTerms.canonical("tiembla")).isEqualTo("sismo");
        assertThat(TutorTerms.canonical("aula")).isEqualTo("salon");
        assertThat(TutorTerms.canonical("elevador")).isEqualTo("ascensor");
        assertThat(TutorTerms.canonical("extintor")).isEqualTo("extintor");
    }

    @Test
    void oneTypoIsFixedInMediumWords() {
        assertThat(TutorTerms.correct("sizmo", VOCABULARY)).isEqualTo("sismo");
        assertThat(TutorTerms.correct("simso", VOCABULARY)).isEqualTo("sismo");
        assertThat(TutorTerms.correct("auditoro", VOCABULARY)).isEqualTo("auditorio");
    }

    @Test
    void twoTyposAreFixedInLongWords() {
        assertThat(TutorTerms.correct("evacuacon", VOCABULARY)).isEqualTo("evacuacion");
        assertThat(TutorTerms.correct("laboratrio", VOCABULARY)).isEqualTo("laboratorio");
    }

    @Test
    void typosInSynonymsAreFixedToo() {
        assertThat(TutorTerms.canonical(TutorTerms.correct("teremoto", VOCABULARY))).isEqualTo("sismo");
    }

    @Test
    void shortAndUnrelatedWordsAreNotChanged() {
        assertThat(TutorTerms.correct("gano", VOCABULARY)).isEqualTo("gano");
        assertThat(TutorTerms.correct("receta", VOCABULARY)).isEqualTo("receta");
        assertThat(TutorTerms.correct("pasta", VOCABULARY)).isEqualTo("pasta");
    }

    @Test
    void distanceCountsSwappedLettersAsOne() {
        assertThat(TutorTerms.distance("sismo", "simso")).isEqualTo(1);
        assertThat(TutorTerms.distance("sismo", "sismo")).isZero();
        assertThat(TutorTerms.distance("evacuacon", "evacuacion")).isEqualTo(1);
    }
}
