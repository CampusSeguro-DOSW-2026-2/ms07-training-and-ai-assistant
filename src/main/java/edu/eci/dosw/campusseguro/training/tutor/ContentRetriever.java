package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.common.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// RF-15 step 4: finds the active contents related to the question. If nothing is close enough the question is out
// of scope and the tutor answers without calling the model (step 5).
//
// By words: compared without accents, case or Spanish stop words, by their root (SpanishStemmer: "fugas" and "fugó"
// are "fuga", "evacuando" is "evacuar"). Synonyms and phrases count as the same word ("se fue la luz" is "energía",
// see tutor/synonyms.txt) and a word that is not in the contents is read as the closest one with a typo or two
// ("sizmo" is "sismo", "fuja" is "fuga").
//
// By meaning: when the question has an embedding, each content also gets the cosine similarity of its own embedding.
// Both are normalized to 0..1 and combined as SEMANTIC_WEIGHT x meaning + (1 - SEMANTIC_WEIGHT) x words, which orders
// the results. A content is used if its words reach MIN_SCORE, as without embeddings, or if the combined score reaches
// TUTOR_SEARCH_MIN_SCORE: the meaning finds more contents, it never hides one the words found.
@Service
@Transactional(readOnly = true)
public class ContentRetriever {

    public static final int MAX_RESULTS = 3;
    public static final int MIN_SCORE = 2;
    public static final int TITLE_POINTS = 3;
    public static final int BODY_POINTS = 1;
    public static final int MAX_TEXT_LENGTH = 1200;

    // Words score that counts as 1 when it is normalized.
    static final int FULL_WORD_SCORE = 8;
    static final double SEMANTIC_WEIGHT = 0.6;
    // Cosine similarities of text-embedding-3-small: below FLOOR the texts are unrelated, above CEILING the same topic.
    static final double SEMANTIC_FLOOR = 0.25;
    static final double SEMANTIC_CEILING = 0.60;

    private static final int MIN_WORD_LENGTH = 3;

    // Words that say nothing about the topic of the question.
    static final Set<String> STOP_WORDS = Set.of(
            "que", "como", "donde", "cuando", "cual", "cuales", "quien", "por", "para", "con", "sin", "una",
            "uno", "unos", "unas", "los", "las", "del", "al", "les", "nos", "mis", "tus", "sus", "mas", "pero",
            "este", "esta", "estos", "estas", "ese", "esa", "eso", "esto", "aqui", "alli", "hay", "ser", "estar",
            "soy", "estoy", "son", "hago", "hacer", "hace", "debo", "debe", "deberia", "puedo", "puede",
            "tengo", "tiene", "tener", "voy", "va", "ir", "algo", "todo", "toda", "todos", "muy", "tambien",
            "entonces", "si", "no", "me", "mi", "tu", "te", "se", "lo", "la", "el", "en", "de", "y", "o", "a",
            "un", "es", "le", "su", "ya", "porque", "sobre", "entre", "hasta", "desde", "hola", "gracias", "favor",
            "ayuda", "ayudame", "dime", "decir", "saber", "quiero", "cosa", "cosas", "casa", "bien", "mal", "nada",
            "alguien", "alguno", "alguna", "otro", "otra", "ahora", "hoy", "ayer", "manana", "dia", "dias", "vez",
            "veces", "mucho", "mucha", "poco", "poca", "pasa", "paso", "pasando", "quedo", "quede", "fue", "era",
            "ella", "ellos", "usted", "ustedes", "nosotros", "yo", "tan", "solo", "sola", "cada", "mismo", "misma",
            "porfa", "pues", "bueno", "buena", "vale", "listo", "creo", "parece", "sera", "seria", "queda", "quedas",
            "quedan", "quedar", "cuanto", "cuanta", "cuantos", "cuantas");

    // Roots of the words of the synonyms file: the topics of the tutor. One of them in the text of a content is
    // enough to use it; any other word must be in the title or come with a second word.
    private static final Set<String> TOPIC_STEMS = TutorTerms.known().stream()
            .map(SpanishStemmer::stem)
            .collect(Collectors.toUnmodifiableSet());

    private static final Logger log = LoggerFactory.getLogger(ContentRetriever.class);

    private final TutorContentRepository repository;
    private final ContentEmbeddings embeddings;
    private final TutorSearchProperties properties;

    public ContentRetriever(TutorContentRepository repository, ContentEmbeddings embeddings,
                            TutorSearchProperties properties) {
        this.repository = repository;
        this.embeddings = embeddings;
        this.properties = properties;
    }

    // By words only. The question must already be cleaned by InputAnalyzer. At most MAX_RESULTS, best first.
    public List<RelatedContent> find(String question) {
        return find(question, null);
    }

    // questionVector is the embedding of the redacted question, or null to search only by words.
    public List<RelatedContent> find(String question, float[] questionVector) {
        List<ContentText> contents = repository.findByStatus(Status.ACTIVO);
        Set<String> wanted = questionStems(question, vocabulary(contents));
        if (wanted.isEmpty() && questionVector == null) {
            return List.of();
        }
        List<Indexed> indexed = contents.stream()
                .map(content -> new Indexed(content, stems(content.getTitle()), stems(content.getBody())))
                .toList();
        Weighted weights = weights(wanted, indexed);
        long start = System.nanoTime();
        List<Rated> rated = indexed.stream()
                .map(content -> rate(content, wanted, weights, questionVector))
                .toList();
        // Only numbers: never the question or the contents
        if (log.isDebugEnabled()) {
            log.debug("Tutor search: {} contents in {} ms, best combined score {}", rated.size(),
                    (System.nanoTime() - start) / 1_000_000,
                    rated.stream().mapToDouble(Rated::combined).max().orElse(0));
        }
        return rated.stream()
                .filter(Rated::relevant)
                .sorted(Comparator.comparingDouble(Rated::combined).reversed()
                        .thenComparing(one -> -one.related().score())
                        .thenComparing(one -> one.related().title()))
                .limit(MAX_RESULTS)
                .map(Rated::related)
                .toList();
    }

    private record Indexed(ContentText content, Set<String> title, Set<String> body) {
    }

    // How much each word of the question is worth: 2 if few contents have it, 3 if very few.
    private record Weighted(Set<String> specific, Set<String> rare) {
    }

    private record Rated(RelatedContent related, double combined, boolean relevant) {
    }

    // Words of the question that appear in few contents ("auditorio") say more about it than words in almost all
    // of them ("sismo"): they count double, and triple if very few contents have them. Only with enough contents for
    // the comparison to mean something.
    private static Weighted weights(Set<String> wanted, List<Indexed> indexed) {
        if (indexed.size() < 4) {
            return new Weighted(Set.of(), Set.of());
        }
        Set<String> specific = new java.util.HashSet<>();
        Set<String> rare = new java.util.HashSet<>();
        for (String stem : wanted) {
            long count = indexed.stream().filter(c -> c.title().contains(stem) || c.body().contains(stem)).count();
            if (count * 8 <= indexed.size()) {
                rare.add(stem);
            } else if (count * 4 <= indexed.size()) {
                specific.add(stem);
            }
        }
        return new Weighted(specific, rare);
    }

    private Rated rate(Indexed indexed, Set<String> wanted, Weighted weights, float[] questionVector) {
        int score = 0;
        int matched = 0;
        boolean inTitle = false;
        boolean topic = false;
        // A word in the title and also in the text counts both: that content is more about the topic
        for (String stem : wanted) {
            int points = 0;
            if (indexed.title().contains(stem)) {
                points += TITLE_POINTS;
                inTitle = true;
            }
            if (indexed.body().contains(stem)) {
                points += BODY_POINTS;
            }
            if (points > 0) {
                matched++;
                topic |= TOPIC_STEMS.contains(stem);
            }
            score += weights.rare().contains(stem) ? points * 3 : weights.specific().contains(stem) ? points * 2 : points;
        }
        // One ordinary word in the text ("celular", "parte") is a coincidence, not the topic of the question
        boolean byWords = score >= MIN_SCORE && (inTitle || topic || matched >= 2);
        ContentText content = indexed.content();
        RelatedContent related = new RelatedContent(content.getId(), content.getTitle(), cut(content.getBody()), score);
        double words = Math.min(1.0, score / (double) FULL_WORD_SCORE);

        float[] contentVector = questionVector == null ? null : embeddings.vectorOf(content.getId()).orElse(null);
        if (contentVector == null) {
            return new Rated(related, words, byWords);
        }
        double meaning = normalizeSimilarity(ContentEmbeddings.cosine(questionVector, contentVector));
        double combined = SEMANTIC_WEIGHT * meaning + (1 - SEMANTIC_WEIGHT) * words;
        return new Rated(related, combined, byWords || combined >= properties.minScore());
    }

    static double normalizeSimilarity(double cosine) {
        double value = (cosine - SEMANTIC_FLOOR) / (SEMANTIC_CEILING - SEMANTIC_FLOOR);
        return Math.max(0, Math.min(1, value));
    }

    static Set<String> stems(String text) {
        return words(text).stream()
                .map(SpanishStemmer::stem)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // Stems of the question, with each unknown word read as the closest word of the contents or the synonyms.
    static Set<String> questionStems(String question, Set<String> vocabulary) {
        Set<String> known = vocabulary.stream().map(SpanishStemmer::stem).collect(Collectors.toSet());
        TutorTerms.known().forEach(word -> known.add(SpanishStemmer.stem(word)));
        return words(question).stream()
                .map(word -> known.contains(SpanishStemmer.stem(word))
                        ? word
                        : TutorTerms.canonical(TutorTerms.correct(word, vocabulary)))
                .map(SpanishStemmer::stem)
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

    // Meaningful words of a text: normalized, phrases joined, without stop words, each one as its synonym.
    private static List<String> words(String text) {
        return Arrays.stream(TutorTerms.phrases(InputAnalyzer.normalize(text)).split(" "))
                .filter(word -> word.length() >= MIN_WORD_LENGTH && !STOP_WORDS.contains(word))
                .map(TutorTerms::canonical)
                .toList();
    }

    private static String cut(String body) {
        String text = body == null ? "" : body.trim();
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH) + "…";
    }
}
