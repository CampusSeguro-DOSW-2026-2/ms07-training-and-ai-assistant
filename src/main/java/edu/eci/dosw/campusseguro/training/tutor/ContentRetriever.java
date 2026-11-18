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
// so "sismo" matches "sismos" and "evacuar" matches "evacuación".
@Service
@Transactional(readOnly = true)
public class ContentRetriever {

    public static final int MAX_RESULTS = 3;
    public static final int MIN_SCORE = 2;
    public static final int TITLE_POINTS = 3;
    public static final int BODY_POINTS = 1;
    public static final int MAX_TEXT_LENGTH = 2000;

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
        Set<String> wanted = stems(question);
        if (wanted.isEmpty()) {
            return List.of();
        }
        return repository.findByStatus(Status.ACTIVO).stream()
                .map(content -> rate(content, wanted))
                .filter(related -> related.score() >= MIN_SCORE)
                .sorted(Comparator.comparingInt(RelatedContent::score).reversed()
                        .thenComparing(RelatedContent::title))
                .limit(MAX_RESULTS)
                .toList();
    }

    private static RelatedContent rate(ContentText content, Set<String> wanted) {
        Set<String> inTitle = stems(content.getTitle());
        Set<String> inBody = stems(content.getBody());
        int score = 0;
        for (String stem : wanted) {
            if (inTitle.contains(stem)) {
                score += TITLE_POINTS;
            } else if (inBody.contains(stem)) {
                score += BODY_POINTS;
            }
        }
        return new RelatedContent(content.getId(), content.getTitle(), cut(content.getBody()), score);
    }

    static Set<String> stems(String text) {
        return Arrays.stream(InputAnalyzer.normalize(text).split(" "))
                .filter(word -> word.length() >= MIN_WORD_LENGTH && !STOP_WORDS.contains(word))
                .map(word -> word.length() > STEM_LENGTH ? word.substring(0, STEM_LENGTH) : word)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String cut(String body) {
        String text = body == null ? "" : body.trim();
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH) + "…";
    }
}
