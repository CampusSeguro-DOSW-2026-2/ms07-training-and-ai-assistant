package edu.eci.dosw.campusseguro.training.tutor.ai;

// Adapter pattern (aaintegration): every AI provider is called through this contract, so the tutor
// does not depend on any provider. A new provider is a new @Component that implements it.
public interface AiAdapter {

    // Name used in LLM_PROVIDER to select this adapter, in lower case.
    String provider();

    // Sends the prompt and returns the text of the answer.
    // Throws DependencyUnavailableException if the provider fails, times out or is not configured.
    String complete(AiPrompt prompt);
}
