package edu.eci.dosw.campusseguro.training.evaluation.draft;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// campusseguro.evaluation-drafts.* from application.properties (LLM_GENERATION_* environment variables).
// Writing 3 to 10 questions takes longer than an answer of the tutor, so it has its own timeout and length.
@ConfigurationProperties(prefix = "campusseguro.evaluation-drafts")
public record EvaluationDraftProperties(Duration timeout, Integer maxTokens) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final int DEFAULT_MAX_TOKENS = 1800;

    public EvaluationDraftProperties {
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
        maxTokens = maxTokens == null || maxTokens <= 0 ? DEFAULT_MAX_TOKENS : maxTokens;
    }
}
