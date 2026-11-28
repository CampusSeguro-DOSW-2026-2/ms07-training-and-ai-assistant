package edu.eci.dosw.campusseguro.training.tutor.ai;

import edu.eci.dosw.campusseguro.training.exception.DependencyUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientAiAdapterTest {

    static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-28T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final class Scripted implements AiAdapter {
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean failing;
        volatile CountDownLatch hold;
        volatile CountDownLatch entered;

        @Override
        public String provider() {
            return "scripted";
        }

        @Override
        public String complete(AiPrompt prompt) {
            calls.incrementAndGet();
            if (entered != null) {
                entered.countDown();
            }
            if (hold != null) {
                try {
                    hold.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failing) {
                throw new DependencyUnavailableException(ChatCompletionsAdapter.UNAVAILABLE);
            }
            return "ok";
        }
    }

    private static final AiPrompt PROMPT = new AiPrompt("reglas", "pregunta");

    private final MovableClock clock = new MovableClock();
    private final Scripted model = new Scripted();
    private final ResilientAiAdapter adapter = new ResilientAiAdapter(model,
            new AiResilienceProperties(2, 5, Duration.ofSeconds(30)), clock);

    private void failTimes(int times) {
        model.failing = true;
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> adapter.complete(PROMPT)).isInstanceOf(DependencyUnavailableException.class);
        }
    }

    @Test
    void afterFiveFailuresInARowTheModelIsNotCalledForThirtySeconds() {
        failTimes(5);
        assertThat(adapter.isOpen()).isTrue();
        model.failing = false;

        assertThatThrownBy(() -> adapter.complete(PROMPT))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessage(ChatCompletionsAdapter.UNAVAILABLE);
        assertThat(model.calls.get()).isEqualTo(5);

        clock.now = clock.now.plusSeconds(31);
        assertThat(adapter.complete(PROMPT)).isEqualTo("ok");
        assertThat(adapter.isOpen()).isFalse();
    }

    @Test
    void aSuccessResetsTheCount() {
        failTimes(4);
        model.failing = false;
        adapter.complete(PROMPT);

        failTimes(4);

        assertThat(adapter.isOpen()).isFalse();
    }

    @Test
    void aFailedTrialOpensTheCircuitAgain() {
        failTimes(5);
        clock.now = clock.now.plusSeconds(31);

        failTimes(1);

        assertThat(adapter.isOpen()).isTrue();
        assertThat(model.calls.get()).isEqualTo(6);
    }

    @Test
    void whenEveryPermitIsInUseTheNextCallAnswersAtOnce() throws Exception {
        model.hold = new CountDownLatch(1);
        model.entered = new CountDownLatch(2);
        Thread first = new Thread(() -> adapter.complete(PROMPT));
        Thread second = new Thread(() -> adapter.complete(PROMPT));
        first.start();
        second.start();
        assertThat(model.entered.await(5, TimeUnit.SECONDS)).isTrue();

        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter.complete(PROMPT)).isInstanceOf(DependencyUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));

        model.hold.countDown();
        first.join();
        second.join();
        assertThat(model.calls.get()).isEqualTo(2);
        assertThat(adapter.complete(PROMPT)).isEqualTo("ok");
    }

    @Test
    void embeddingsAndModerationPassThrough() {
        assertThat(adapter.supportsEmbeddings()).isFalse();
        assertThat(adapter.moderate("texto").available()).isFalse();
        assertThat(adapter.provider()).isEqualTo("scripted");
    }

    @Test
    void propertiesHaveDefaults() {
        AiResilienceProperties defaults = AiResilienceProperties.defaults();

        assertThat(defaults.maxConcurrent()).isEqualTo(8);
        assertThat(defaults.circuitFailures()).isEqualTo(5);
        assertThat(defaults.circuitOpen()).isEqualTo(Duration.ofSeconds(30));
    }
}
