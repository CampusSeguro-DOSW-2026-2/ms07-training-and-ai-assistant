package edu.eci.dosw.campusseguro.training.tutor.ai;

import edu.eci.dosw.campusseguro.training.exception.DependencyUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Semaphore;

// Decorator of the chosen adapter that protects MS07 and the provider (RF-15), without new dependencies:
// - at most maxConcurrent calls to the model at once; one more answers 503 at once instead of piling up;
// - a circuit breaker: after circuitFailures failures in a row the model is not called for circuitOpen and the tutor
//   answers "not available" at once, without waiting for the timeout. Then one call is tried again: if it works the
//   circuit closes, if it fails it opens again.
// Embeddings and moderation pass through: they have their own fallback and never block an answer.
class ResilientAiAdapter implements AiAdapter {

    private static final Logger log = LoggerFactory.getLogger(ResilientAiAdapter.class);

    private final AiAdapter delegate;
    private final AiResilienceProperties properties;
    private final Clock clock;
    private final Semaphore permits;
    private int consecutiveFailures;
    private Instant openUntil = Instant.MIN;
    private boolean trialInProgress;

    ResilientAiAdapter(AiAdapter delegate, AiResilienceProperties properties, Clock clock) {
        this.delegate = delegate;
        this.properties = properties;
        this.clock = clock;
        this.permits = new Semaphore(properties.maxConcurrent());
    }

    AiAdapter delegate() {
        return delegate;
    }

    @Override
    public String provider() {
        return delegate.provider();
    }

    @Override
    public String complete(AiPrompt prompt) {
        boolean trial = admit();
        if (!permits.tryAcquire()) {
            release(trial);
            log.warn("AI provider {} is busy: {} calls in progress", provider(), properties.maxConcurrent());
            throw new DependencyUnavailableException(ChatCompletionsAdapter.UNAVAILABLE);
        }
        try {
            String answer = delegate.complete(prompt);
            succeeded();
            return answer;
        } catch (RuntimeException e) {
            failed();
            throw e;
        } finally {
            permits.release();
        }
    }

    // Closed: anyone passes. Open: nobody passes until the time is over; then a single call tries again.
    private synchronized boolean admit() {
        Instant now = clock.instant();
        if (now.isBefore(openUntil)) {
            throw new DependencyUnavailableException(ChatCompletionsAdapter.UNAVAILABLE);
        }
        if (consecutiveFailures >= properties.circuitFailures()) {
            if (trialInProgress) {
                throw new DependencyUnavailableException(ChatCompletionsAdapter.UNAVAILABLE);
            }
            trialInProgress = true;
            return true;
        }
        return false;
    }

    private synchronized void release(boolean trial) {
        if (trial) {
            trialInProgress = false;
        }
    }

    private synchronized void succeeded() {
        consecutiveFailures = 0;
        trialInProgress = false;
        openUntil = Instant.MIN;
    }

    private synchronized void failed() {
        consecutiveFailures++;
        trialInProgress = false;
        if (consecutiveFailures >= properties.circuitFailures()) {
            openUntil = clock.instant().plus(properties.circuitOpen());
            log.warn("AI provider {} failed {} times in a row: not called for {} s", provider(), consecutiveFailures,
                    properties.circuitOpen().toSeconds());
        }
    }

    synchronized boolean isOpen() {
        return clock.instant().isBefore(openUntil);
    }

    @Override
    public boolean supportsEmbeddings() {
        return delegate.supportsEmbeddings();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return delegate.embed(texts);
    }

    @Override
    public String embeddingModel() {
        return delegate.embeddingModel();
    }

    @Override
    public Moderation moderate(String text) {
        return delegate.moderate(text);
    }
}
