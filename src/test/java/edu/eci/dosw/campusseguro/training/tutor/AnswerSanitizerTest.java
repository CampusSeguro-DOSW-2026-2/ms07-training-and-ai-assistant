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
