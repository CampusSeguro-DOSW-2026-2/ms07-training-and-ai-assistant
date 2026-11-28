package edu.eci.dosw.campusseguro.training.tutor;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// RF-15: what the model writes is never shown as it comes. The answer is plain text (the contents are plain text
// too): HTML tags and Markdown marks are removed, and so are links that are not in the contents used.
public final class AnswerSanitizer {

    private static final Pattern CODE_FENCE = Pattern.compile("```[\\p{Alnum}_+-]*");
    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern HTML_TAG = Pattern.compile("</?\\p{Alpha}[^<>]{0,300}>|<!--.*?-->", Pattern.DOTALL);
    private static final Pattern MARKDOWN_LINK = Pattern.compile("!?\\[([^\\]\\n]{0,200})]\\([^)\\s]{0,500}\\)");
    private static final Pattern HEADING = Pattern.compile("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*");
    private static final Pattern LIST_MARK = Pattern.compile("(?m)^[ \\t]*(?:[*+]|\\d{1,2}[.)])[ \\t]+");
    private static final Pattern EMAIL = Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+");
    // A figure: "123", "10:30", "1.000", "300 123 4567" (digits with spaces, dots, dashes or slashes between them)
    private static final Pattern FIGURE = Pattern.compile("(?<![\\p{L}\\d])\\+?\\d+(?:[ .,:/-]?\\d+)*(?![\\p{L}\\d])");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    // Less letters than this after removing what is not in the contents: the answer says nothing
    static final int MIN_LETTERS = 20;
    private static final Pattern EMPHASIS = Pattern.compile("\\*{1,3}|_{2,3}|~~|`");
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)[^\\s<>\"«»]*[^\\s<>\"«».,;:!?)]"
                    + "|\\b[\\p{Alnum}-]+(?:\\.[\\p{Alnum}-]+)*\\.(?:com|co|org|net|edu|gov|io|info)(?:/[^\\s<>\"«»]*)?\\b");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f]+");
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile(" +([.,;:!?)])");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n+");

    private AnswerSanitizer() {
    }

    // Plain text without tags, Markdown or links unknown to the sources.
    public static String plainText(String text, Collection<String> sources) {
        if (text == null) {
            return "";
        }
        String result = CODE_FENCE.matcher(text).replaceAll("");
        result = SCRIPT.matcher(result).replaceAll(" ");
        result = HTML_TAG.matcher(result).replaceAll(" ");
        result = MARKDOWN_LINK.matcher(result).replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
        result = HEADING.matcher(result).replaceAll("");
        result = LIST_MARK.matcher(result).replaceAll("- ");
        result = EMPHASIS.matcher(result).replaceAll("");
        result = removeUnknownLinks(result, sources);
        result = result.replace('<', ' ').replace('>', ' ');
        result = SPACES.matcher(result).replaceAll(" ");
        result = SPACE_BEFORE_PUNCTUATION.matcher(result).replaceAll("$1");
        result = BLANK_LINES.matcher(result).replaceAll("\n");
        return result.lines().map(String::strip).filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }

    // Anchors the answer to the contents (RF-15): e-mails, phone numbers and other figures that are not in the
    // contents used are removed, so the model cannot make up a phone line or a number. Returns "" if what is left
    // has fewer than MIN_LETTERS letters.
    public static String grounded(String text, Collection<String> sources) {
        String known = String.join("\n", sources.stream().filter(java.util.Objects::nonNull).toList());
        String result = EMAIL.matcher(text).replaceAll(match ->
                known.contains(match.group()) ? Matcher.quoteReplacement(match.group()) : "");
        result = FIGURE.matcher(result).replaceAll(match ->
                appears(match.group(), known) ? Matcher.quoteReplacement(match.group()) : "");
        result = SPACES.matcher(result).replaceAll(" ");
        result = SPACE_BEFORE_PUNCTUATION.matcher(result).replaceAll("$1");
        result = result.lines().map(String::strip).filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + "\n" + b).orElse("");
        return LETTER.matcher(result).results().count() < MIN_LETTERS ? "" : result;
    }

    // The figure is in the contents as a whole number, not inside another one: "3" is not in "2023".
    private static boolean appears(String figure, String known) {
        return Pattern.compile("(?<!\\d)" + Pattern.quote(figure) + "(?!\\d)").matcher(known).find();
    }

    // At most max characters, cut after a whole word and closed with "…".
    public static String shorten(String text, int max) {
        if (text.codePointCount(0, text.length()) <= max) {
            return text;
        }
        int end = text.offsetByCodePoints(0, max - 1);
        int lastSpace = Math.max(text.lastIndexOf(' ', end), text.lastIndexOf('\n', end));
        String cut = lastSpace > 0 ? text.substring(0, lastSpace) : text.substring(0, end);
        return cut.replaceAll("[\\s,;:.-]+$", "") + "…";
    }

    private static String removeUnknownLinks(String text, Collection<String> sources) {
        Matcher matcher = URL.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String link = matcher.group();
            boolean known = sources.stream().anyMatch(source -> source != null && source.contains(link));
            matcher.appendReplacement(result, known ? Matcher.quoteReplacement(link) : "");
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
