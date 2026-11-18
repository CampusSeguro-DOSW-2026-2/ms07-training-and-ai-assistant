package edu.eci.dosw.campusseguro.training.evaluation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

// Body to answer an evaluation: one option per question. Messages are user-facing, so they stay in Spanish.
public record AttemptRequest(
        @NotNull(message = "Las respuestas son obligatorias.")
        List<@NotNull(message = "La respuesta no puede ser nula.") @Valid AnswerRequest> answers) {

    public record AnswerRequest(
            @NotNull(message = "La pregunta es obligatoria.")
            UUID questionId,

            @NotNull(message = "La opción es obligatoria.")
            UUID optionId) {
    }
}
