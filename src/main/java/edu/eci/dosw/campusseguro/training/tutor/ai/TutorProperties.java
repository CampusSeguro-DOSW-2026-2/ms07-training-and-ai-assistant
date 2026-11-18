package edu.eci.dosw.campusseguro.training.tutor.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Locale;

// campusseguro.tutor.* from application.properties (LLM_* environment variables).
// The API key only comes from the environment: it is never written in the repository.
@ConfigurationProperties(prefix = "campusseguro.tutor")
public record TutorProperties(String provider, String url, String apiKey, String model, Duration timeout,
                              Integer maxTokens, Double temperature) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(2500);

    public TutorProperties {
        provider = provider == null || provider.isBlank() ? "openai" : provider.trim().toLowerCase(Locale.ROOT);
        url = url == null ? "" : url.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null ? "" : model.trim();
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        maxTokens = maxTokens == null ? 250 : maxTokens;
        temperature = temperature == null ? 0.2 : temperature;
    }

    // The key must never reach a log line or an error message.
    @Override
    public String toString() {
        return "TutorProperties[provider=" + provider + ", url=" + url + ", model=" + model + ", timeout=" + timeout
                + ", maxTokens=" + maxTokens + ", temperature=" + temperature + ", apiKey=****]";
    }
}
