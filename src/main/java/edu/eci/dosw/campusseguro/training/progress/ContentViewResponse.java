package edu.eci.dosw.campusseguro.training.progress;

import java.time.Instant;
import java.util.UUID;

public record ContentViewResponse(UUID contentId, Instant firstViewedAt, Instant lastViewedAt) {

    public static ContentViewResponse from(ContentView view) {
        return new ContentViewResponse(view.getContent().getId(), view.getFirstViewedAt(), view.getLastViewedAt());
    }
}
