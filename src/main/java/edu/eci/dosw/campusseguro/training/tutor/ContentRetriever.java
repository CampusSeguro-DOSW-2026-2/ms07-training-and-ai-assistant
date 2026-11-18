package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.common.Status;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// RF-15 step 4: finds the active contents related to the question. If nothing reaches MIN_SCORE the
// question is out of scope and the tutor answers without calling the model (step 5).
// Words are compared without accents, case or Spanish stop words, by their first STEM_LENGTH letters,
// so "sismo" matches "sismos" and "evacuar" matches "evacuación". Synonyms count as the same word
// ("terremoto" is "sismo") and a word of the question that is not in the contents is read as the closest one
// with a typo or two ("sizmo" is "sismo"), see TutorTerms.
@Service
@Transactional(readOnly = true)
public class ContentRetriever {

    public static final int MAX_RESULTS = 3;
    public static final int MIN_SCORE = 2;
    public static final int TITLE_POINTS = 3;
    public static final int BODY_POINTS = 1;
    public static final int MAX_TEXT_LENGTH = 1200;

    private static final int STEM_LENGTH = 5;
    private static final int MIN_WORD_LENGTH = 3;

    // Words that say nothing about the topic of the question.
    private static final Set<String> STOP_WORDS = Set.of(
            "que", "como", "donde", "cuando", "cual", "cuales", "quien", "por", "para", "con", "sin", "una",
            "uno", "unos", "unas", "los", "las", "del", "al", "les", "nos", "mis", "tus", "sus", "mas", "pero",
            "este", "esta", "estos", "estas", "ese", "esa", "eso", "esto", "aqui", "alli", "hay", "ser", "estar",
            "soy", "estoy", "son", "hago", "hacer", "hace", "debo", "debe", "deberia", "puedo", "puede",
            "tengo", "tiene", "tener", "voy", "va", "ir", "algo", "todo", "toda", "todos", "muy", "tambien",
            "entonces", "si", "no", "me", "mi", "tu", "te", "se", "lo", "la", "el", "en", "de", "y", "o", "a",
            "un", "es", "le", "su", "ya", "porque", "sobre", "entre", "hasta", "desde", "hola", "gracias", "favor", "ayuda", "ayudame", "dime", "decir", "saber", "quiero");

    private final TutorContentRepository repository;

    public ContentRetriever(TutorContentRepository repository) {
        this.repository = repository;
    }

    // The question must already be cleaned by InputAnalyzer. Returns at most MAX_RESULTS, best first.
    public List<RelatedContent> find(String question) {
        List<ContentText> contents = repository.findByStatus(Status.ACTIVO);
        Set<String> wanted = questionStems(question, vocabulary(contents));
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<Indexed> indexed = contents.stream()
                .map(content -> new Indexed(content, stems(content.getTitle()), stems(content.getBody())))
                .toList();
        Set<String> specific = specific(wanted, indexed);
        return indexed.stream()
                .map(content -> rate(content, wanted, specific))
                .filter(related -> related.score() >= MIN_SCORE)
                .sorted(Comparator.comparingInt(RelatedContent::score).reversed()
                        .thenComparing(RelatedContent::title))
                .limit(MAX_RESULTS)
                .toList();
    }

    private record Indexed(ContentText content, Set<String> title, Set<String> body) {
    }

    // Words of the question that appear in few contents ("auditorio") say more about it than words in almost all
    // of them ("sismo"): they count double. Only with enough contents for the comparison to mean something.
    private static Set<String> specific(Set<String> wanted, List<Indexed> indexed) {
        if (indexed.size() < 4) {
            return Set.of();
        }
        return wanted.stream()
                .filter(stem -> indexed.stream()
                        .filter(c -> c.title().contains(stem) || c.body().contains(stem))
                        .count() * 4 <= indexed.size())
                .collect(Collectors.toSet());
    }

    private static RelatedContent rate(Indexed indexed, Set<String> wanted, Set<String> specific) {
        int score = 0;
        // A word in the title and also in the text counts both: that content is more about the topic
        for (String stem : wanted) {
            int points = 0;
            if (indexed.title().contains(stem)) {
                points += TITLE_POINTS;
            }
            if (indexed.body().contains(stem)) {
                points += BODY_POINTS;
            }
            score += specific.contains(stem) ? points * 2 : points;
        }
        ContentText content = indexed.content();
        return new RelatedContent(content.getId(), content.getTitle(), cut(content.getBody()), score);
    }

    static Set<String> stems(String text) {
        return words(text).stream()
                .map(ContentRetriever::stem)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // Stems of the question, with each unknown word read as the closest word of the contents.
    static Set<String> questionStems(String question, Set<String> vocabulary) {
        Set<String> known = vocabulary.stream().map(ContentRetriever::stem).collect(Collectors.toSet());
        return words(question).stream()
                .map(word -> known.contains(stem(word)) ? word : TutorTerms.canonical(TutorTerms.correct(word, vocabulary)))
                .map(ContentRetriever::stem)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // Every meaningful word of the active contents, already as its synonym.
    static Set<String> vocabulary(List<ContentText> contents) {
        Set<String> vocabulary = new LinkedHashSet<>();
        for (ContentText content : contents) {
            vocabulary.addAll(words(content.getTitle()));
            vocabulary.addAll(words(content.getBody()));
        }
        return vocabulary;
    }

    // Meaningful words of a text: normalized, without stop words, each one as its synonym.
    private static List<String> words(String text) {
        return Arrays.stream(InputAnalyzer.normalize(text).split(" "))
                .filter(word -> word.length() >= MIN_WORD_LENGTH && !STOP_WORDS.contains(word))
                .map(TutorTerms::canonical)
                .toList();
    }

    private static String stem(String word) {
        return word.length() > STEM_LENGTH ? word.substring(0, STEM_LENGTH) : word;
    }

    private static String cut(String body) {
        String text = body == null ? "" : body.trim();
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH) + "…";
    }
}
