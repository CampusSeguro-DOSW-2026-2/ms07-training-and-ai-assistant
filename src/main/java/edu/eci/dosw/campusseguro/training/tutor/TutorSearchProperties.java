package edu.eci.dosw.campusseguro.training.tutor;

import org.springframework.boot.context.properties.ConfigurationProperties;

// campusseguro.tutor.search.* (TUTOR_SEARCH_MIN_SCORE): the least combined score (0 to 1) a content needs to be used
// when the search by meaning is on. Higher is stricter.
@ConfigurationProperties(prefix = "campusseguro.tutor.search")
public record TutorSearchProperties(Double minScore) {

    public static final double DEFAULT_MIN_SCORE = 0.35;

    public TutorSearchProperties {
        minScore = minScore == null || minScore <= 0 || minScore >= 1 ? DEFAULT_MIN_SCORE : minScore;
    }
}
