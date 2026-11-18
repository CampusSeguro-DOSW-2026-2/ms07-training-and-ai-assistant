package edu.eci.dosw.campusseguro.training.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Result of an attempt, with the correction of each answer.
public record AttemptResponse(UUID id, UUID evaluationId, String topic, int score, int correctAnswers,
                              int totalQuestions, Instant attemptedAt, List<AnswerResult> answers) {

    public record AnswerResult(UUID questionId, UUID optionId, boolean correct, UUID correctOptionId) {
    }
}
