package edu.eci.dosw.campusseguro.training.tutor;

import org.junit.jupiter.api.Test;

import java.util.List;
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
    void wordsOfOtherEmergenciesBecomeTheWordOfTheContents() {
        assertThat(List.of("fuego", "llamas", "humo")).allMatch(word -> TutorTerms.canonical(word).equals("incendio"));
        assertThat(TutorTerms.canonical("escape")).isEqualTo("fuga");
        assertThat(List.of("riega", "rego", "derrama")).allMatch(word -> TutorTerms.canonical(word).equals("derrame"));
        assertThat(List.of("quimico", "quimicos", "reactivo", "reactivos"))
                .allMatch(word -> TutorTerms.canonical(word).equals("sustancia"));
        assertThat(List.of("desmayo", "herido", "sangre")).allMatch(word -> TutorTerms.canonical(word).equals("lesion"));
        assertThat(TutorTerms.canonical("extintores")).isEqualTo("extintor");
        assertThat(TutorTerms.canonical("sirena")).isEqualTo("alarma");
        assertThat(TutorTerms.canonical("inundaciones")).isEqualTo("inundacion");
    }

    @Test
    void theSynonymsFileHasEveryTopic() {
        assertThat(List.of("conato", "quemazon")).allMatch(word -> TutorTerms.canonical(word).equals("incendio"));
        assertThat(List.of("goteo", "escape")).allMatch(word -> TutorTerms.canonical(word).equals("fuga"));
        assertThat(List.of("solvente", "acido")).allMatch(word -> TutorTerms.canonical(word).equals("sustancia"));
        assertThat(List.of("inconsciente", "fractura", "sangra"))
                .allMatch(word -> TutorTerms.canonical(word).equals("lesion"));
        assertThat(TutorTerms.canonical("apagon")).isEqualTo("energia");
        assertThat(TutorTerms.canonical("filtracion")).isEqualTo("inundacion");
        assertThat(TutorTerms.canonical("bomba")).isEqualTo("amenaza");
        assertThat(TutorTerms.canonical("rescatista")).isEqualTo("brigadista");
        assertThat(TutorTerms.canonical("fuegos")).isEqualTo("incendio");
    }

    @Test
    void phrasesBecomeOneWord() {
        assertThat(TutorTerms.phrases("se fue la luz en el salon")).isEqualTo("energia en el salon");
        assertThat(TutorTerms.phrases("hay olor a gas")).isEqualTo("hay fuga");
        assertThat(TutorTerms.phrases("donde esta la zona segura")).isEqualTo("donde esta la puntodeencuentro");
        assertThat(TutorTerms.phrases("vi un paquete sospechoso")).isEqualTo("vi un amenaza");
        assertThat(TutorTerms.phrases("un companero se desmayo")).isEqualTo("un companero lesion");
        assertThat(TutorTerms.phrases("se rego un reactivo")).isEqualTo("derrame un reactivo");
    }

    @Test
    void fourLetterWordsGetOneTypoOnlyAgainstKnownWordsWithTheSameFirstLetter() {
        Set<String> vocabulary = Set.of("fuga", "gas", "humo", "piso");

        assertThat(TutorTerms.correct("fuja", vocabulary)).isEqualTo("fuga");
        assertThat(TutorTerms.correct("fugs", vocabulary)).isEqualTo("fuga");
        assertThat(TutorTerms.correct("ruga", vocabulary)).isEqualTo("ruga");
        assertThat(TutorTerms.correct("pato", vocabulary)).isEqualTo("pato");
        assertThat(TutorTerms.correct("mesa", vocabulary)).isEqualTo("mesa");
        assertThat(TutorTerms.correct("sal", vocabulary)).isEqualTo("sal");
    }

    @Test
    void typosOfTopicWordsAreFixed() {
        assertThat(TutorTerms.correct("incendo", Set.of())).isEqualTo("incendio");
        assertThat(TutorTerms.correct("evaquar", Set.of())).isEqualTo("evacuar");
        assertThat(TutorTerms.correct("sizmo", Set.of())).isEqualTo("sismo");
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
