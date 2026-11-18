package edu.eci.dosw.campusseguro.training.evaluation;

import edu.eci.dosw.campusseguro.training.common.Status;

import java.time.Instant;
import java.util.UUID;

public record EvaluationSummaryResponse(UUID id, String title, String topic, UUID categoryId, Status status,
                                        Instant updatedAt) {

    public static EvaluationSummaryResponse from(Evaluation evaluation) {
        return new EvaluationSummaryResponse(evaluation.getId(), evaluation.getTitle(), evaluation.getTopic(),
                evaluation.getCategory().getId(), evaluation.getStatus(), evaluation.getUpdatedAt());
    }
}
