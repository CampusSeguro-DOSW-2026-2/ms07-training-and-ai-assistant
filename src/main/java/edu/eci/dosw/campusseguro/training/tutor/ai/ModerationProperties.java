package edu.eci.dosw.campusseguro.training.tutor.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// campusseguro.tutor.moderation.* (LLM_MODERATION_*, TUTOR_MODERATION_ENABLED): the free moderation endpoint of
// OpenAI, used on the redacted question and on the answer of the model. The key is LLM_API_KEY.
@ConfigurationProperties(prefix = "campusseguro.tutor.moderation")
public record ModerationProperties(Boolean enabled, String url, String model, Duration timeout) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(2000);

    public ModerationProperties {
        enabled = enabled == null || enabled;
        url = url == null ? "" : url.trim();
        model = model == null || model.isBlank() ? "omni-moderation-latest" : model.trim();
        timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
    }

    public static ModerationProperties disabled() {
        return new ModerationProperties(false, "", null, null);
    }
}
