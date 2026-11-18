package edu.eci.dosw.campusseguro.training.content;

import edu.eci.dosw.campusseguro.training.common.Status;

import java.time.Instant;
import java.util.UUID;

public record ContentResponse(UUID id, String title, String body, UUID categoryId, Status status,
                              Instant createdAt, Instant updatedAt) {

    public static ContentResponse from(Content content) {
        return new ContentResponse(content.getId(), content.getTitle(), content.getBody(),
                content.getCategory().getId(), content.getStatus(),
                content.getCreatedAt(), content.getUpdatedAt());
    }
}
