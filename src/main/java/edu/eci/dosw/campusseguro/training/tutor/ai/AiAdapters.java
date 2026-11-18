package edu.eci.dosw.campusseguro.training.tutor.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

// Independent Components pattern (aaintegration): receives every adapter and keeps the one chosen in
// LLM_PROVIDER. Adding a provider does not change this class or the tutor.
@Component
public class AiAdapters {

    private final AiAdapter current;

    public AiAdapters(List<AiAdapter> adapters, TutorProperties properties) {
        this.current = adapters.stream()
                .filter(adapter -> adapter.provider().equals(properties.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown LLM_PROVIDER '" + properties.provider()
                        + "'. Available: " + adapters.stream().map(AiAdapter::provider)
                        .collect(Collectors.joining(", "))));
    }

    public AiAdapter current() {
        return current;
    }
}
