package edu.eci.dosw.campusseguro.training.tutor.ai;

import java.util.Set;

// Result of the moderation of a text. available is false when the provider has no moderation or it failed:
// then nothing is blocked. categories are the names the provider marked ("self-harm", "violence"...).
public record Moderation(boolean available, boolean flagged, Set<String> categories) {

    public static Moderation unavailable() {
        return new Moderation(false, false, Set.of());
    }

    public static Moderation clean() {
        return new Moderation(true, false, Set.of());
    }

    public static Moderation flagged(Set<String> categories) {
        return new Moderation(true, true, Set.copyOf(categories));
    }
}
