package edu.eci.dosw.campusseguro.training.content;

import edu.eci.dosw.campusseguro.training.common.Status;

import java.time.Instant;
import java.util.UUID;

// List item: everything except the body.
public record ContentSummaryResponse(UUID id, String title, UUID categoryId, Status status, Instant updatedAt) {

    public static ContentSummaryResponse from(Content content) {
        return new ContentSummaryResponse(content.getId(), content.getTitle(), content.getCategory().getId(),
                content.getStatus(), content.getUpdatedAt());
    }
}
