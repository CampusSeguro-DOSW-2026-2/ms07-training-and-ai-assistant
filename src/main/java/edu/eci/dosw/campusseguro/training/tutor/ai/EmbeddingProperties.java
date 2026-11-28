package edu.eci.dosw.campusseguro.training.tutor.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// campusseguro.tutor.embedding.* (LLM_EMBEDDING_* environment variables): the embeddings the tutor uses to find
// contents by meaning. Without URL or key the search works only with words. The key is LLM_API_KEY.
@ConfigurationProperties(prefix = "campusseguro.tutor.embedding")
public record EmbeddingProperties(String url, String model, Duration timeout) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(2000);

    public EmbeddingProperties {
        url = url == null ? "" : url.trim();
        model = model == null || model.isBlank() ? "text-embedding-3-small" : model.trim();
        timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
    }

    // No URL: embeddings are off and the search uses only words.
    public static EmbeddingProperties disabled() {
        return new EmbeddingProperties("", null, null);
    }
}
