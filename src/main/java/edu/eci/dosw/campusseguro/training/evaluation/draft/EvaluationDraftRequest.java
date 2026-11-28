package edu.eci.dosw.campusseguro.training.evaluation.draft;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Body of POST /api/v1/training/evaluation-drafts. questions is 5 when it is not sent.
public record EvaluationDraftRequest(
        @NotNull(message = "La categoría es obligatoria.")
        UUID categoryId,

        @Min(value = EvaluationDraftRequest.MIN_QUESTIONS, message = "El número de preguntas debe estar entre 3 y 10.")
        @Max(value = EvaluationDraftRequest.MAX_QUESTIONS, message = "El número de preguntas debe estar entre 3 y 10.")
        Integer questions,

        @Size(max = 150, message = "El tema debe tener máximo 150 caracteres.")
        String topic) {

    public static final int MIN_QUESTIONS = 3;
    public static final int MAX_QUESTIONS = 10;
    public static final int DEFAULT_QUESTIONS = 5;

    public int questionCount() {
        return questions == null ? DEFAULT_QUESTIONS : questions;
    }

    public String cleanTopic() {
        return topic == null || topic.isBlank() ? null : topic.strip();
    }
}
