package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.common.SlidingWindowRateLimiter;
import edu.eci.dosw.campusseguro.training.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

// RF-15: questions per user (X-User-Id), by default 10 per minute and 100 per day
// (TUTOR_RATE_PER_MINUTE, TUTOR_RATE_PER_DAY). Over the limit the tutor answers 429 without calling the model.
@Component
public class TutorRateLimiter {

    static final String PER_MINUTE_MESSAGE =
            "Hizo varias preguntas seguidas. Espere un momento y vuelva a intentarlo.";
    static final String PER_DAY_MESSAGE =
            "Alcanzó el límite de preguntas al tutor por hoy. Vuelva a intentarlo mañana.";

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final SlidingWindowRateLimiter limiter;

    @Autowired
    public TutorRateLimiter(TutorSafetyProperties properties) {
        this(properties, Clock.systemUTC());
    }

    TutorRateLimiter(TutorSafetyProperties properties, Clock clock) {
        this.limiter = new SlidingWindowRateLimiter(clock, List.of(
                new SlidingWindowRateLimiter.Window(MINUTE, properties.ratePerMinute()),
                new SlidingWindowRateLimiter.Window(Duration.ofDays(1), properties.ratePerDay())));
    }

    // Counts the question, or throws TooManyRequestsException (429 DEMASIADAS_SOLICITUDES).
    public void check(UUID userId) {
        limiter.tryAcquire(userId.toString()).ifPresent(window -> {
            throw new TooManyRequestsException(window.length().equals(MINUTE) ? PER_MINUTE_MESSAGE : PER_DAY_MESSAGE);
        });
    }
}
