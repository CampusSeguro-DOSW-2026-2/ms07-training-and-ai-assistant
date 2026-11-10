package edu.eci.dosw.campusseguro.training.category;

import edu.eci.dosw.campusseguro.training.common.Status;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, String description, Status status) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.getStatus());
    }
}
