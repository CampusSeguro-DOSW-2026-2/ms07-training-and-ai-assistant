package edu.eci.dosw.campusseguro.training.tutor;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// RF-15: personal data never leaves MS07. Before the question goes to the model, links, e-mails, card numbers,
// phones, ID numbers and names given explicitly are replaced by a mark. The contents search still uses the
// original question because it stays inside MS07.
// Short numbers of the domain ("piso 3", "salón 205", "1990") are kept: an ID has 6 to 10 digits.
@Component
public class PersonalDataRedactor {

    public static final String LINK = "[enlace]";
    public static final String EMAIL = "[correo]";
    public static final String CARD = "[tarjeta]";
    public static final String PHONE = "[teléfono]";
    public static final String DOCUMENT = "[documento]";
    public static final String NAME = "[nombre]";

    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)[^\\s<>\"«»]*[^\\s<>\"«».,;:!?)]");

    private static final Pattern MAIL = Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+");

    // 13 to 19 digits, optionally grouped with spaces or hyphens. Only replaced when they pass the Luhn check.
    private static final Pattern CARD_NUMBER = Pattern.compile("(?<![\\d.,])\\d(?:[ -]?\\d){12,18}(?![\\d.,])");

    // An ID announced by its name: "mi cédula es 1.234.567", "C.C. 1234567", "T.I. 1002003004".
    // Only the number is replaced, so the question still reads well.
    private static final Pattern DOCUMENT_WITH_LABEL = Pattern.compile(
            "(?iu)(\\b(?:c[eé]dula(?:\\s+de\\s+ciudadan[ií]a)?|c\\.\\s?c\\.?|cc|t\\.\\s?i\\.?"
                    + "|tarjeta\\s+de\\s+identidad|documento(?:\\s+de\\s+identidad)?|identificaci[oó]n)"
                    + "(?:\\s+(?:es|n[uú]mero|no\\.?|n[°º]))?\\s*[:#]?\\s*)"
                    + "(\\d{1,3}(?:[.\\s]?\\d{3}){1,3}|\\d{6,10})(?![\\d])");

    private static final String SEP = "[ .-]?";

    // Colombian mobiles (3xx xxx xxxx) and the national landline format (60x xxx xxxx), with or without +57.
    private static final Pattern PHONE_NUMBER = Pattern.compile(
            "(?<![\\d+])(?:(?:\\+\\s?57|\\b57)" + SEP + ")?(?:\\(\\s?(?:3\\d{2}|60\\d)\\s?\\)|3\\d{2}|60\\d)"
                    + SEP + "\\d{3}" + SEP + "\\d{4}(?!\\d)");

    // Old 7 digit landlines are only read as phones after a phone word or a +57 prefix:
    // "salón 205 1990" must not change.
    private static final String OLD_LANDLINE =
            "(?:\\d{3}" + SEP + "\\d{2}" + SEP + "\\d{2}|\\d{3}" + SEP + "\\d{4})(?!\\d)";
    private static final Pattern PHONE_WITH_LABEL = Pattern.compile(
            "(?iu)(\\b(?:tel[eé]fono|tel\\.?|celular|cel\\.?|fijo|whats?app|n[uú]mero)\\s*(?:es|:)?\\s*)"
                    + "(" + OLD_LANDLINE + ")");
    private static final Pattern PHONE_WITH_PREFIX = Pattern.compile(
            "\\+\\s?57\\s?(?:\\(?\\d\\)?\\s?)?" + OLD_LANDLINE);

    // Any other number of 6 to 10 digits, with or without thousands dots, is read as an ID ("1.000" is not).
    private static final Pattern BARE_DOCUMENT = Pattern.compile(
            "(?<![\\d.,])(?:\\d{3}\\.\\d{3}|\\d{1,3}(?:\\.\\d{3}){2,3}|\\d{6,10})(?![\\d,]|\\.\\d)");

    private static final String PROPER_NAME = "\\p{Lu}[\\p{Ll}\\p{M}'’-]+";

    // "me llamo Ana María", "mi nombre es Juan de la Torre". The trigger ignores case; the name must be capitalized.
    private static final Pattern GIVEN_NAME = Pattern.compile(
            "(?iu)(\\b(?:me\\s+llamo|mi\\s+nombre\\s+es)\\s+)(?-i)(" + PROPER_NAME
                    + "(?:\\s+(?:(?:de|del|de\\s+la|de\\s+los)\\s+)?" + PROPER_NAME + "){0,3})");

    // "soy Ana Gómez": "soy" needs two capitalized words, so "soy Brigadista" or "soy de Ingeniería" stay.
    private static final Pattern INTRODUCED_NAME = Pattern.compile(
            "(?iu)(\\bsoy\\s+)(?-i)(" + PROPER_NAME + "(?:\\s+" + PROPER_NAME + "){1,3})");

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = URL.matcher(text).replaceAll(Matcher.quoteReplacement(LINK));
        result = MAIL.matcher(result).replaceAll(Matcher.quoteReplacement(EMAIL));
        result = replaceCards(result);
        result = DOCUMENT_WITH_LABEL.matcher(result).replaceAll(keepLabel(DOCUMENT));
        result = PHONE_NUMBER.matcher(result).replaceAll(Matcher.quoteReplacement(PHONE));
        result = PHONE_WITH_PREFIX.matcher(result).replaceAll(Matcher.quoteReplacement(PHONE));
        result = PHONE_WITH_LABEL.matcher(result).replaceAll(keepLabel(PHONE));
        result = BARE_DOCUMENT.matcher(result).replaceAll(Matcher.quoteReplacement(DOCUMENT));
        result = GIVEN_NAME.matcher(result).replaceAll(keepLabel(NAME));
        result = INTRODUCED_NAME.matcher(result).replaceAll(keepLabel(NAME));
        return result;
    }

    private static String keepLabel(String mark) {
        return "$1" + Matcher.quoteReplacement(mark);
    }

    private static String replaceCards(String text) {
        Matcher matcher = CARD_NUMBER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("[ -]", "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(passesLuhn(digits) ? CARD : matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean twice = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (twice) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            twice = !twice;
        }
        return sum % 10 == 0;
    }
}
