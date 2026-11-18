package edu.eci.dosw.campusseguro.training.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body to create or update a category. Messages are user-facing, so they stay in Spanish.
public record CategoryRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre debe tener máximo 100 caracteres.")
        String name,

        @Size(max = 500, message = "La descripción debe tener máximo 500 caracteres.")
        String description) {
}
