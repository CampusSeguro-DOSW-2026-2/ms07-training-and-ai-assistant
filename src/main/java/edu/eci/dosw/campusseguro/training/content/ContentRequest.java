package edu.eci.dosw.campusseguro.training.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Body to create or update a content. Messages are user-facing, so they stay in Spanish.
public record ContentRequest(
        @NotBlank(message = "El título es obligatorio.")
        @Size(max = 150, message = "El título debe tener máximo 150 caracteres.")
        String title,

        @NotBlank(message = "El cuerpo es obligatorio.")
        @Size(max = 20000, message = "El cuerpo debe tener máximo 20000 caracteres.")
        String body,

        @NotNull(message = "La categoría es obligatoria.")
        UUID categoryId) {
}
