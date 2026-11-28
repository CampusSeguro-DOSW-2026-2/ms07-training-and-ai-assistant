package edu.eci.dosw.campusseguro.training.tutor;

import java.util.List;

// Light Spanish stemmer for the tutor search: words of the same family end in the same root, so
// "fuga", "fugas" and "fugó" are "fug", and "evacuar", "evacuación" and "evacuando" are "evacu".
// Words must already be normalized (lower case, no accents). It is not a full Snowball stemmer on purpose: it only
// removes plurals, diminutives, frequent verb endings and "-ción", and never leaves a root shorter than 3 letters.
final class SpanishStemmer {

    private static final int MIN_ROOT = 3;

    // Longest first: the first one that fits is removed.
    private static final List<String> SUFFIXES = List.of(
            "amientos", "imientos", "amiento", "imiento",
            "aciones", "iciones", "uciones", "ciones", "acion", "icion", "ucion", "cion",
            "adoras", "adores", "adora", "ador",
            "iendo", "ando",
            "illas", "illos", "itas", "itos", "illa", "illo", "ita", "ito",
            "adas", "ados", "idas", "idos", "ada", "ado", "ida", "ido",
            "aron", "ieron", "aba", "ia",
            "ar", "er", "ir");

    private SpanishStemmer() {
    }

    static String stem(String word) {
        if (word == null || word.length() <= MIN_ROOT) {
            return word;
        }
        String root = withoutPlural(word);
        for (String suffix : SUFFIXES) {
            if (root.endsWith(suffix) && root.length() - suffix.length() >= MIN_ROOT) {
                root = root.substring(0, root.length() - suffix.length());
                break;
            }
        }
        // A final vowel is the gender or the verb person: "fuga", "fugo" and "fugue" share "fug". Not "i":
        // "incendio" and "incendiando" must both end in "incendi"
        char last = root.charAt(root.length() - 1);
        if ((last == 'a' || last == 'e' || last == 'o') && root.length() - 1 >= MIN_ROOT) {
            root = root.substring(0, root.length() - 1);
        }
        return root;
    }

    // "luces" -> "luz", "salones" -> "salon", "fugas" -> "fuga".
    static String withoutPlural(String word) {
        if (word.length() <= MIN_ROOT + 1) {
            return word.endsWith("s") && word.length() > MIN_ROOT ? word.substring(0, word.length() - 1) : word;
        }
        if (word.endsWith("ces")) {
            return word.substring(0, word.length() - 3) + "z";
        }
        if (word.endsWith("es") && !isVowel(word.charAt(word.length() - 3))) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("s")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }
}
