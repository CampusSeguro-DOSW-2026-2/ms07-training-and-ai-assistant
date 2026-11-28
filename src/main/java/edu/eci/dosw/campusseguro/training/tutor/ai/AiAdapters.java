package edu.eci.dosw.campusseguro.training.tutor.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;

// Independent Components pattern (aaintegration): receives every adapter and keeps the one chosen in
// LLM_PROVIDER. Adding a provider does not change this class or the tutor. The chosen one is wrapped in
// ResilientAiAdapter (concurrency limit and circuit breaker).
@Component
public class AiAdapters {

    private final AiAdapter current;

    public AiAdapters(List<AiAdapter> adapters, TutorProperties properties) {
        this(adapters, properties, AiResilienceProperties.defaults());
    }

    @Autowired
    public AiAdapters(List<AiAdapter> adapters, TutorProperties properties, AiResilienceProperties resilience) {
        AiAdapter chosen = adapters.stream()
                .filter(adapter -> adapter.provider().equals(properties.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown LLM_PROVIDER '" + properties.provider()
                        + "'. Available: " + adapters.stream().map(AiAdapter::provider)
                        .collect(Collectors.joining(", "))));
        this.current = new ResilientAiAdapter(chosen, resilience, Clock.systemUTC());
    }

    public AiAdapter current() {
        return current;
    }

    // The adapter of the provider itself, without the protections. Only to know which one was chosen.
    AiAdapter chosen() {
        return ((ResilientAiAdapter) current).delegate();
    }
}
