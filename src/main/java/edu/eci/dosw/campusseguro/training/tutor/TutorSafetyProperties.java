package edu.eci.dosw.campusseguro.training.tutor;

import org.springframework.boot.context.properties.ConfigurationProperties;

// campusseguro.tutor.safety.* from application.properties (TUTOR_* environment variables).
@ConfigurationProperties(prefix = "campusseguro.tutor.safety")
public record TutorSafetyProperties(Integer ratePerMinute, Integer ratePerDay, Integer maxAnswerChars) {

    public static final int DEFAULT_RATE_PER_MINUTE = 10;
    public static final int DEFAULT_RATE_PER_DAY = 100;
    public static final int DEFAULT_MAX_ANSWER_CHARS = 1200;

    public TutorSafetyProperties {
        ratePerMinute = positiveOr(ratePerMinute, DEFAULT_RATE_PER_MINUTE);
        ratePerDay = positiveOr(ratePerDay, DEFAULT_RATE_PER_DAY);
        maxAnswerChars = positiveOr(maxAnswerChars, DEFAULT_MAX_ANSWER_CHARS);
    }

    private static int positiveOr(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
