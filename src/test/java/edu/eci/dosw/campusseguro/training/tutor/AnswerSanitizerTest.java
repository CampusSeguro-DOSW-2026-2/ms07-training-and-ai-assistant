package edu.eci.dosw.campusseguro.training.tutor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerSanitizerTest {

    private static final List<String> SOURCES = List.of("Qué hacer durante un sismo",
            "Agáchate y cúbrete. Más información en https://www.escuelaing.edu.co/protocolo");

    @Test
    void removesMarkdown() {
        String answer = "## Durante un sismo\n**Agáchate**, cúbrete y __sujétate__.\n* No uses el ascensor.";

        assertThat(AnswerSanitizer.plainText(answer, SOURCES))
                .isEqualTo("Durante un sismo\nAgáchate, cúbrete y sujétate.\n- No uses el ascensor.");
    }

    @Test
    void removesCodeBlocks() {
        assertThat(AnswerSanitizer.plainText("```python\nprint('hola')\n``` Agáchate `ya`.", SOURCES))
                .isEqualTo("print('hola')\nAgáchate ya.");
    }

    @Test
    void removesHtmlTags() {
        assertThat(AnswerSanitizer.plainText("<b>Agáchate</b> y <script>alert(1)</script>cúbrete<br/>.", SOURCES))
                .isEqualTo("Agáchate y cúbrete.");
    }

    @Test
    void removesLinksThatAreNotInTheContents() {
        assertThat(AnswerSanitizer.plainText("Lea https://evil.test/x y [esto](http://otro.test). Agáchate.", SOURCES))
                .isEqualTo("Lea y esto. Agáchate.");
        assertThat(AnswerSanitizer.plainText("Visite www.otra-pagina.com o campus.org para más.", SOURCES))
                .isEqualTo("Visite o para más.");
    }

    @Test
    void keepsLinksThatAreInTheContents() {
        assertThat(AnswerSanitizer.plainText("Vea https://www.escuelaing.edu.co/protocolo.", SOURCES))
                .isEqualTo("Vea https://www.escuelaing.edu.co/protocolo.");
    }

    @Test
    void plainTextStaysTheSame() {
        String answer = "Agáchate, cúbrete y sujétate. Aléjate de ventanas (y estantes) en el piso 3.";

        assertThat(AnswerSanitizer.plainText(answer, SOURCES)).isEqualTo(answer);
    }

    @Test
    void groundedKeepsOnlyFiguresAndEmailsOfTheContents() {
        List<String> sources = List.of("Llama a la línea de emergencias 123. Revisa el kit cada 6 meses. "
                + "Escribe a brigada@escuelaing.edu.co.");

        assertThat(AnswerSanitizer.grounded("Llama al 123 o al 300 123 4567 y escribe a brigada@escuelaing.edu.co "
                + "o a otro@mail.com. Revisa el kit cada 6 meses y 2 semanas.", sources))
                .isEqualTo("Llama al 123 o al y escribe a brigada@escuelaing.edu.co o a. "
                        + "Revisa el kit cada 6 meses y semanas.");
    }

    @Test
    void aFigureMustAppearWhole() {
        List<String> sources = List.of("Simulacro de 2023 en el piso 12.");

        assertThat(AnswerSanitizer.grounded("Ve al piso 3 o al piso 12 de la sede principal.", sources))
                .isEqualTo("Ve al piso o al piso 12 de la sede principal.");
    }

    @Test
    void anAnswerWithAlmostNoWordsLeftIsEmpty() {
        assertThat(AnswerSanitizer.grounded("Llama al 601 555 0000.", List.of("Agáchate."))).isEmpty();
    }

    @Test
    void numberedListsBecomePlainLines() {
        assertThat(AnswerSanitizer.plainText("1. Agáchate.\n2) Cúbrete.", SOURCES)).isEqualTo("- Agáchate.\n- Cúbrete.");
    }

    @Test
    void shortensWithoutCuttingAWord() {
        String text = "Agáchate cúbrete y sujétate hasta que pase";

        String shortened = AnswerSanitizer.shorten(text, 20);

        assertThat(shortened).isEqualTo("Agáchate cúbrete y…").hasSizeLessThanOrEqualTo(20);
        assertThat(AnswerSanitizer.shorten(text, 200)).isEqualTo(text);
    }

    @Test
    void shortensAWordLongerThanTheLimit() {
        assertThat(AnswerSanitizer.shorten("a".repeat(30), 10)).isEqualTo("a".repeat(9) + "…");
    }
}
