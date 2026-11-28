package edu.eci.dosw.campusseguro.training.common;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowRateLimiterTest {

    // A clock the test moves by hand.
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-28T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

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

    private static final SlidingWindowRateLimiter.Window MINUTE =
            new SlidingWindowRateLimiter.Window(Duration.ofMinutes(1), 3);
    private static final SlidingWindowRateLimiter.Window DAY =
            new SlidingWindowRateLimiter.Window(Duration.ofDays(1), 5);

    private final MovableClock clock = new MovableClock();
    private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock, List.of(MINUTE, DAY));

    @Test
    void acceptsUpToTheLimitPerMinute() {
        assertThat(limiter.tryAcquire("ana")).isEmpty();
        assertThat(limiter.tryAcquire("ana")).isEmpty();
        assertThat(limiter.tryAcquire("ana")).isEmpty();

        assertThat(limiter.tryAcquire("ana")).contains(MINUTE);
    }

    @Test
    void eachKeyHasItsOwnLimit() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("ana");
        }

        assertThat(limiter.tryAcquire("luis")).isEmpty();
    }

    @Test
    void theWindowSlides() {
        limiter.tryAcquire("ana");
        clock.advance(Duration.ofSeconds(30));
        limiter.tryAcquire("ana");
        limiter.tryAcquire("ana");
        assertThat(limiter.tryAcquire("ana")).contains(MINUTE);

        // The first use leaves the window; the other two are still inside it
        clock.advance(Duration.ofSeconds(31));
        assertThat(limiter.tryAcquire("ana")).isEmpty();
        assertThat(limiter.tryAcquire("ana")).contains(MINUTE);
    }

    @Test
    void rejectedUsesAreNotCounted() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("ana");
        }
        clock.advance(Duration.ofMinutes(2));

        assertThat(limiter.tryAcquire("ana")).isEmpty();
    }

    @Test
    void theDailyLimitAppliesAcrossMinutes() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("ana")).isEmpty();
            clock.advance(Duration.ofMinutes(2));
        }

        assertThat(limiter.tryAcquire("ana")).contains(DAY);

        clock.advance(Duration.ofDays(1));
        assertThat(limiter.tryAcquire("ana")).isEmpty();
    }

    @Test
    void oldKeysAreRemoved() {
        limiter.tryAcquire("ana");
        limiter.tryAcquire("luis");
        assertThat(limiter.trackedKeys()).isEqualTo(2);

        clock.advance(Duration.ofDays(1).plus(SlidingWindowRateLimiter.CLEANUP_EVERY));
        limiter.tryAcquire("marta");

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }
}
