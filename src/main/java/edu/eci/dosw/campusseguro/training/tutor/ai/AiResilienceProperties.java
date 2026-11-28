package edu.eci.dosw.campusseguro.training.tutor.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// campusseguro.tutor.resilience.* (TUTOR_AI_*): how many calls to the model can be in progress at once, and the
// circuit that stops calling it for a while after several failures in a row.
@ConfigurationProperties(prefix = "campusseguro.tutor.resilience")
public record AiResilienceProperties(Integer maxConcurrent, Integer circuitFailures, Duration circuitOpen) {

    public static final int DEFAULT_MAX_CONCURRENT = 8;
    public static final int DEFAULT_CIRCUIT_FAILURES = 5;
    public static final Duration DEFAULT_CIRCUIT_OPEN = Duration.ofSeconds(30);

    public AiResilienceProperties {
        maxConcurrent = maxConcurrent == null || maxConcurrent <= 0 ? DEFAULT_MAX_CONCURRENT : maxConcurrent;
        circuitFailures = circuitFailures == null || circuitFailures <= 0 ? DEFAULT_CIRCUIT_FAILURES : circuitFailures;
        circuitOpen = circuitOpen == null || circuitOpen.isNegative() || circuitOpen.isZero()
                ? DEFAULT_CIRCUIT_OPEN : circuitOpen;
    }

    public static AiResilienceProperties defaults() {
        return new AiResilienceProperties(null, null, null);
    }
}
