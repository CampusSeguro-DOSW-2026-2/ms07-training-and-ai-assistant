package edu.eci.dosw.campusseguro.training.tutor.ai;

import java.util.List;

// Adapter pattern (aaintegration): every AI provider is called through this contract, so the tutor
// does not depend on any provider. A new provider is a new @Component that implements it.
public interface AiAdapter {

    // Name used in LLM_PROVIDER to select this adapter, in lower case.
    String provider();

    // Sends the prompt and returns the text of the answer.
    // Throws DependencyUnavailableException if the provider fails, times out or is not configured.
    String complete(AiPrompt prompt);

    // True when this provider can turn texts into embeddings (it has the URL and the key).
    default boolean supportsEmbeddings() {
        return false;
    }

    // One vector per text, in the same order. Throws DependencyUnavailableException if the provider fails.
    // Never receives personal data: the tutor sends the question already redacted.
    default List<float[]> embed(List<String> texts) {
        throw new UnsupportedOperationException("Embeddings are not available for " + provider());
    }

    // Name of the embedding model, stored with each vector so a new model recalculates them.
    default String embeddingModel() {
        return "";
    }

    // Moderation of a text (a redacted question or an answer of the model). Unavailable when the provider has no
    // moderation or it fails: the tutor then follows its normal flow.
    default Moderation moderate(String text) {
        return Moderation.unavailable();
    }
}
