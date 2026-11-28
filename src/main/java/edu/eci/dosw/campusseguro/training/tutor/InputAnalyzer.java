package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

// Input Validation pattern (aaintegration), adapted to RF-15: the question cannot be empty or longer
// than 500 characters. Unlike the original, accents, ñ and ¿? are kept, and the question is not
// rewritten with a second call to the model.
@Component
public class InputAnalyzer {

    public static final String FIELD = "question";
    public static final int MAX_LENGTH = 500;

    // Control characters (line breaks, tabs...) become spaces; invisible format characters (zero-width spaces,
    // direction marks) are removed, so "sis​mo" is "sismo" and cannot hide words from the checks.
    private static final Pattern CONTROL = Pattern.compile("\\p{Cc}");
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");
    private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    // Returns the question ready to send to the tutor: one line, single spaces, no invisible characters.
    // 400 VALIDACION if it is empty or has more than 500 characters.
    public String clean(String question) {
        String visible = question == null ? "" : INVISIBLE.matcher(question).replaceAll("");
        String cleaned = SPACES.matcher(CONTROL.matcher(visible).replaceAll(" "))
                .replaceAll(" ")
                .trim();
        if (cleaned.isEmpty()) {
            throw new BadRequestException(FIELD, "La pregunta es obligatoria.");
        }
        if (cleaned.codePointCount(0, cleaned.length()) > MAX_LENGTH) {
            throw new BadRequestException(FIELD, "La pregunta debe tener máximo " + MAX_LENGTH + " caracteres.");
        }
        return cleaned;
    }

    // Text to compare questions with contents: lower case, no accents and only letters, digits and spaces.
    // "¿Qué hago en el AUDITORIO?" -> "que hago en el auditorio".
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutMarks = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return NOT_WORD.matcher(withoutMarks.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }
}
