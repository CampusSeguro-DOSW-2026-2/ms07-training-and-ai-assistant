package edu.eci.dosw.campusseguro.training.tutor;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SpanishStemmerTest {

    @ParameterizedTest(name = "{0} and {1} share the root")
    @CsvSource({
            "fuga, fugas", "fuga, fugo", "fuga, fugs",
            "evacuar, evacuacion", "evacuar, evacuando", "evacuar, evacuaciones", "evacuar, evacua",
            "sismo, sismos", "salon, salones", "lesion, lesiones", "luz, luces", "extintor, extintores",
            "derrame, derrama", "derrame, derramado", "incendio, incendios", "incendio, incendiando",
            "laboratorio, laboratorios", "ascensor, ascensores", "inundacion, inundando", "brigadista, brigadistas",
            "salida, salidas", "clase, clases", "papelito, papel", "camilla, camillas",
    })
    void wordsOfTheSameFamilyShareTheRoot(String one, String other) {
        assertThat(SpanishStemmer.stem(other)).isEqualTo(SpanishStemmer.stem(one));
    }

    @ParameterizedTest(name = "{0} and {1} stay different")
    @CsvSource({
            "sismo, salon", "fuga, fuego", "gas, gato", "piso, paso", "humo, hombre", "luz, lugar",
    })
    void differentWordsKeepDifferentRoots(String one, String other) {
        assertThat(SpanishStemmer.stem(other)).isNotEqualTo(SpanishStemmer.stem(one));
    }

    @ParameterizedTest
    @CsvSource({"gas, gas", "luz, luz", "lab, lab", "sal, sal"})
    void shortWordsAreNotCut(String word, String root) {
        assertThat(SpanishStemmer.stem(word)).isEqualTo(root);
    }
}
