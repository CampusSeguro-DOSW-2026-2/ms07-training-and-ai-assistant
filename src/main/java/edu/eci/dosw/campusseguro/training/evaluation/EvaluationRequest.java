package edu.eci.dosw.campusseguro.training.evaluation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Body to create or update an evaluation. The rules on the number of questions, options and correct
// answers are checked in EvaluationService (422). Messages are user-facing, so they stay in Spanish.
public record EvaluationRequest(
        @NotBlank(message = "El título es obligatorio.")
        @Size(max = 150, message = "El título debe tener máximo 150 caracteres.")
        String title,

        @NotBlank(message = "El tema es obligatorio.")
        @Size(max = 150, message = "El tema debe tener máximo 150 caracteres.")
        String topic,

        @NotNull(message = "La categoría es obligatoria.")
        UUID categoryId,

        @Size(max = 50, message = "La evaluación debe tener máximo 50 preguntas.")
        List<@NotNull(message = "La pregunta no puede ser nula.") @Valid QuestionRequest> questions) {

    public record QuestionRequest(
            @NotBlank(message = "El enunciado es obligatorio.")
            @Size(max = 500, message = "El enunciado debe tener máximo 500 caracteres.")
            String statement,

            @Size(max = 10, message = "Cada pregunta debe tener máximo 10 opciones.")
            List<@NotNull(message = "La opción no puede ser nula.") @Valid OptionRequest> options) {
    }

    public record OptionRequest(
            @NotBlank(message = "El texto de la opción es obligatorio.")
            @Size(max = 300, message = "El texto de la opción debe tener máximo 300 caracteres.")
            String text,

            boolean correct) {
    }
}
