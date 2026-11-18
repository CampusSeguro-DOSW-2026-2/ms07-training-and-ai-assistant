package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// RF-15 step 3: the question is not empty and has at most 500 characters.
class InputAnalyzerTest {

    private final InputAnalyzer analyzer = new InputAnalyzer();

    @Test
    void keepsAccentsEnyeAndQuestionMarks() {
        assertThat(analyzer.clean("¿Qué hace un niño en el auditorio si tiembla?"))
                .isEqualTo("¿Qué hace un niño en el auditorio si tiembla?");
    }

    @Test
    void trimsAndJoinsLinesAndSpaces() {
        assertThat(analyzer.clean("  ¿Por dónde\n\n salgo   del\tlaboratorio? "))
                .isEqualTo("¿Por dónde salgo del laboratorio?");
    }

    @Test
    void removesInvisibleCharacters() {
        assertThat(analyzer.clean("¿Dónde​ es\u0007 el punto de encuentro?"))
                .isEqualTo("¿Dónde es el punto de encuentro?");
    }

    @Test
    void emptyQuestionIsRejected() {
        for (String question : new String[]{null, "", "   ", "\n\t", "​​"}) {
            assertThatThrownBy(() -> analyzer.clean(question))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("La pregunta es obligatoria.")
                    .extracting("field").isEqualTo(InputAnalyzer.FIELD);
        }
    }

    @Test
    void fiveHundredCharactersAreAccepted() {
        String question = "a".repeat(InputAnalyzer.MAX_LENGTH);

        assertThat(analyzer.clean("   " + question + "   ")).hasSize(InputAnalyzer.MAX_LENGTH);
    }

    @Test
    void moreThanFiveHundredCharactersAreRejected() {
        String question = "a".repeat(InputAnalyzer.MAX_LENGTH + 1);

        assertThatThrownBy(() -> analyzer.clean(question))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La pregunta debe tener máximo 500 caracteres.");
    }

    @Test
    void anEmojiCountsAsOneCharacter() {
        String question = "a".repeat(InputAnalyzer.MAX_LENGTH - 1) + "🔥";

        assertThat(analyzer.clean(question)).endsWith("🔥");
    }

    @Test
    void normalizeRemovesAccentsCaseAndPunctuation() {
        assertThat(InputAnalyzer.normalize("¿Qué hago en el AUDITORIO, niño?"))
                .isEqualTo("que hago en el auditorio nino");
    }

    @Test
    void normalizeOfNothingIsEmpty() {
        assertThat(InputAnalyzer.normalize(null)).isEmpty();
        assertThat(InputAnalyzer.normalize("¿?¡!")).isEmpty();
    }
}
