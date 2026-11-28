package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TutorRateLimiterTest {

    private static final UUID USER = UUID.randomUUID();

    @Test
    void defaultsAreTenPerMinuteAndOneHundredPerDay() {
        TutorSafetyProperties defaults = new TutorSafetyProperties(null, 0, -5);

        assertThat(defaults.ratePerMinute()).isEqualTo(10);
        assertThat(defaults.ratePerDay()).isEqualTo(100);
        assertThat(defaults.maxAnswerChars()).isEqualTo(1200);
    }

    @Test
    void theEleventhQuestionInAMinuteIsRejected() {
        TutorRateLimiter limiter = new TutorRateLimiter(new TutorSafetyProperties(null, null, null));
        for (int i = 0; i < 10; i++) {
            assertThatCode(() -> limiter.check(USER)).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> limiter.check(USER))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage(TutorRateLimiter.PER_MINUTE_MESSAGE);
    }

    @Test
    void theDailyLimitHasItsOwnMessage() {
        Instant start = Instant.parse("2026-11-28T08:00:00Z");
        Instant[] now = {start};
        Clock clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now[0];
            }
        };
        TutorRateLimiter limiter = new TutorRateLimiter(new TutorSafetyProperties(10, 3, null), clock);
        for (int i = 0; i < 3; i++) {
            limiter.check(USER);
            now[0] = now[0].plus(Duration.ofMinutes(5));
        }

        assertThatThrownBy(() -> limiter.check(USER)).hasMessage(TutorRateLimiter.PER_DAY_MESSAGE);
    }
}
