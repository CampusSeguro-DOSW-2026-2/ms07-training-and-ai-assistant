package edu.eci.dosw.campusseguro.training.tutor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

// Words of the tutor domain: synonyms that mean the same in a question and in the contents, and tolerance to typos.
// Words are already normalized by InputAnalyzer.normalize (lower case, no accents).
final class TutorTerms {

    // Word as people write it -> word used in the contents. Applied to questions and contents alike.
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            entry("sismos", "sismo"), entry("terremoto", "sismo"), entry("terremotos", "sismo"),
            entry("temblor", "sismo"), entry("temblores", "sismo"), entry("tiembla", "sismo"),
            entry("tiemble", "sismo"), entry("temblando", "sismo"), entry("temblo", "sismo"),
            entry("telurico", "sismo"), entry("teluricos", "sismo"), entry("sismico", "sismo"),
            entry("sismica", "sismo"), entry("aula", "salon"), entry("aulas", "salon"), entry("clase", "salon"),
            entry("clases", "salon"), entry("salones", "salon"), entry("lab", "laboratorio"),
            entry("labs", "laboratorio"), entry("laboratorios", "laboratorio"), entry("desalojar", "evacuar"),
            entry("desalojo", "evacuar"), entry("desalojen", "evacuar"), entry("elevador", "ascensor"),
            entry("elevadores", "ascensor"), entry("ascensores", "ascensor"), entry("herido", "lesion"),
            entry("heridos", "lesion"), entry("herida", "lesion"), entry("heridas", "lesion"),
            entry("lastimado", "lesion"), entry("lastimada", "lesion"), entry("lesionado", "lesion"),
            entry("lesionada", "lesion"), entry("lesiones", "lesion"), entry("auditorios", "auditorio"),
            entry("oficinas", "oficina"),
            // Every campus emergency, not only earthquakes: the words people use -> the words of the contents
            entry("fuego", "incendio"), entry("llamas", "incendio"), entry("humo", "incendio"),
            entry("incendios", "incendio"), entry("quema", "incendio"), entry("quemando", "incendio"),
            entry("escape", "fuga"), entry("escapes", "fuga"), entry("fugas", "fuga"),
            entry("riega", "derrame"), entry("rego", "derrame"), entry("regado", "derrame"),
            entry("regada", "derrame"), entry("derrama", "derrame"), entry("derramo", "derrame"),
            entry("derrames", "derrame"), entry("derramado", "derrame"), entry("derramada", "derrame"),
            entry("quimico", "sustancia"), entry("quimicos", "sustancia"), entry("quimica", "sustancia"),
            entry("quimicas", "sustancia"), entry("reactivo", "sustancia"), entry("reactivos", "sustancia"),
            entry("sustancias", "sustancia"), entry("acido", "sustancia"), entry("acidos", "sustancia"),
            entry("inundaciones", "inundacion"), entry("inundado", "inundacion"), entry("inundada", "inundacion"),
            entry("aguas", "agua"),
            entry("desmayo", "lesion"), entry("desmayos", "lesion"), entry("desmayado", "lesion"),
            entry("desmayada", "lesion"), entry("sangre", "lesion"), entry("sangrado", "lesion"),
            entry("sangrando", "lesion"), entry("golpeado", "lesion"), entry("golpeada", "lesion"),
            entry("quemadura", "lesion"), entry("quemaduras", "lesion"),
            entry("extintores", "extintor"), entry("alarmas", "alarma"), entry("sirena", "alarma"),
            entry("sirenas", "alarma"), entry("simulacros", "simulacro"));

    private TutorTerms() {
    }

    static String canonical(String word) {
        return SYNONYMS.getOrDefault(word, word);
    }

    // Typos allowed for a word of that length: none for short words, so "gano" never becomes "gana".
    static int allowedTypos(int length) {
        if (length < 5) {
            return 0;
        }
        return length < 8 ? 1 : 2;
    }

    // The closest known word (a synonym or a word of the contents) within the allowed typos, or the word itself.
    static String correct(String word, Collection<String> vocabulary) {
        int allowed = allowedTypos(word.length());
        if (allowed == 0 || SYNONYMS.containsKey(word) || vocabulary.contains(canonical(word))) {
            return word;
        }
        String best = word;
        int bestDistance = allowed + 1;
        for (Collection<String> known : List.of(SYNONYMS.keySet(), vocabulary)) {
            for (String candidate : known) {
                if (Math.abs(candidate.length() - word.length()) > allowed) {
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
}
