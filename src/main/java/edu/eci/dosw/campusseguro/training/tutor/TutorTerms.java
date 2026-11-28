package edu.eci.dosw.campusseguro.training.tutor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Words of the tutor domain: synonyms that mean the same in a question and in the contents, and tolerance to typos.
// The synonyms live in src/main/resources/tutor/synonyms.txt, one group per line, and are loaded when MS07 starts.
// Words are already normalized by InputAnalyzer.normalize (lower case, no accents).
final class TutorTerms {

    static final String RESOURCE = "/tutor/synonyms.txt";

    // Word as people write it -> word used in the contents. Applied to questions and contents alike.
    private static final Map<String, String> SYNONYMS;
    // Phrases of several words ("se fue la luz") -> one word, longest first.
    private static final List<Map.Entry<String, String>> PHRASES;

    static {
        Map<String, String> words = new HashMap<>();
        Map<String, String> phrases = new LinkedHashMap<>();
        load(words, phrases);
        SYNONYMS = Collections.unmodifiableMap(words);
        PHRASES = phrases.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
                .toList();
    }

    private TutorTerms() {
    }

    // The word of the contents for a word of the question: by itself, or else without its plural ("fuegos").
    static String canonical(String word) {
        String direct = SYNONYMS.get(word);
        if (direct != null) {
            return direct;
        }
        return SYNONYMS.getOrDefault(SpanishStemmer.withoutPlural(word), word);
    }

    // Replaces the known phrases of a normalized text by their word: "se fue la luz" -> "energia".
    static String phrases(String normalized) {
        String text = " " + normalized + " ";
        for (Map.Entry<String, String> phrase : PHRASES) {
            text = text.replace(" " + phrase.getKey() + " ", " " + phrase.getValue() + " ");
        }
        return text.trim();
    }

    // Every word the synonyms know: the ones people write and the ones of the contents.
    static Set<String> known() {
        Set<String> known = new java.util.HashSet<>(SYNONYMS.keySet());
        known.addAll(SYNONYMS.values());
        return known;
    }

    // Typos allowed for a word of that length: none under 4 letters, so "sal" never becomes "gas".
    static int allowedTypos(int length) {
        if (length < 4) {
            return 0;
        }
        return length < 8 ? 1 : 2;
    }

    // The closest known word (a synonym or a word of the contents) within the allowed typos, or the word itself.
    // A word of 4 letters only changes into a word that starts with the same letter ("fuja" -> "fuga").
    static String correct(String word, Collection<String> vocabulary) {
        int allowed = allowedTypos(word.length());
        if (allowed == 0 || SYNONYMS.containsKey(word) || vocabulary.contains(canonical(word))) {
            return word;
        }
        boolean shortWord = word.length() == 4;
        String best = word;
        int bestDistance = allowed + 1;
        for (Collection<String> candidates : List.of(SYNONYMS.keySet(), SYNONYMS.values(), vocabulary)) {
            for (String candidate : candidates) {
                if (candidate.indexOf(' ') >= 0 || Math.abs(candidate.length() - word.length()) > allowed
                        || (shortWord && (candidate.length() < 4 || candidate.charAt(0) != word.charAt(0)))) {
                    continue;
                }
                int distance = distance(word, candidate);
                if (distance < bestDistance || (distance == bestDistance && candidate.compareTo(best) < 0)) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return bestDistance <= allowed ? best : word;
    }

    // Edits to turn a into b: insert, delete, replace or swap two neighbour letters ("sismo" / "simso" = 1).
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    // "canonical: synonym, synonym phrase, ..." per line; "#" starts a comment.
    private static void load(Map<String, String> words, Map<String, String> phrases) {
        try (InputStream in = TutorTerms.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String text = line.strip();
                if (text.isEmpty() || text.startsWith("#") || !text.contains(":")) {
                    continue;
                }
                String canonical = InputAnalyzer.normalize(text.substring(0, text.indexOf(':'))).replace(" ", "");
                // The word of the contents is its own synonym, so its plural also finds it ("inundaciones")
                words.put(canonical, canonical);
                for (String item : text.substring(text.indexOf(':') + 1).split(",")) {
                    String synonym = InputAnalyzer.normalize(item);
                    if (synonym.isEmpty() || synonym.equals(canonical)) {
                        continue;
                    }
                    (synonym.contains(" ") ? phrases : words).put(synonym, canonical);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
