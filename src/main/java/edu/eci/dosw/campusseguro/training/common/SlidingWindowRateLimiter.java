package edu.eci.dosw.campusseguro.training.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// In-memory usage limit per key (a user id), with sliding windows: "10 per minute and 100 per day" keeps the
// time of each accepted use for one day and counts the ones inside each window. Rejected uses are not counted.
// Keys without recent uses are removed every CLEANUP_EVERY, so the map does not grow forever.
// Each instance of MS07 counts on its own; that is enough for one VM per environment.
public class SlidingWindowRateLimiter {

    public record Window(Duration length, int max) {
    }

    static final Duration CLEANUP_EVERY = Duration.ofMinutes(5);

    private final Clock clock;
    private final List<Window> windows;
    private final Duration longest;
    private final Map<String, Deque<Instant>> uses = new ConcurrentHashMap<>();
    private volatile Instant nextCleanup;

    public SlidingWindowRateLimiter(Clock clock, List<Window> windows) {
        if (windows.isEmpty()) {
            throw new IllegalArgumentException("At least one window is required");
        }
        this.clock = clock;
        this.windows = List.copyOf(windows);
        this.longest = windows.stream().map(Window::length).max(Comparator.naturalOrder()).orElseThrow();
        this.nextCleanup = clock.instant().plus(CLEANUP_EVERY);
    }

    // Counts one use for the key if every window still has room. Otherwise returns the first full window
    // and does not count it.
    public Optional<Window> tryAcquire(String key) {
        Instant now = clock.instant();
        cleanupIfDue(now);
        Window[] full = new Window[1];
        // compute runs atomically for the key, so two requests of the same user cannot both take the last place.
        uses.compute(key, (k, current) -> {
            Deque<Instant> times = current != null ? current : new ArrayDeque<>();
            dropOlderThan(times, now.minus(longest));
            full[0] = firstFull(times, now);
            if (full[0] == null) {
                times.addLast(now);
            }
            return times.isEmpty() ? null : times;
        });
        return Optional.ofNullable(full[0]);
    }

    private Window firstFull(Deque<Instant> times, Instant now) {
        for (Window window : windows) {
            Instant since = now.minus(window.length());
            long inside = times.stream().filter(time -> time.isAfter(since)).count();
            if (inside >= window.max()) {
                return window;
            }
        }
        return null;
    }

    int trackedKeys() {
        return uses.size();
    }

    private void cleanupIfDue(Instant now) {
        if (now.isBefore(nextCleanup)) {
            return;
        }
        nextCleanup = now.plus(CLEANUP_EVERY);
        Instant oldest = now.minus(longest);
        for (String key : uses.keySet()) {
            uses.computeIfPresent(key, (k, times) -> {
                dropOlderThan(times, oldest);
                return times.isEmpty() ? null : times;
            });
        }
    }

    private static void dropOlderThan(Deque<Instant> times, Instant oldest) {
        while (!times.isEmpty() && !times.getFirst().isAfter(oldest)) {
            times.removeFirst();
        }
    }
}
