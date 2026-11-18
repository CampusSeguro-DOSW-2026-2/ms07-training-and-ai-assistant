package edu.eci.dosw.campusseguro.training.progress;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// RF-13: progress of the current user. "available" is false when the content or evaluation was deactivated later.
public record ProgressResponse(List<ViewedContent> contents, List<EvaluationResult> evaluations) {

    public record ViewedContent(UUID contentId, String title, UUID categoryId, Instant firstViewedAt,
                                Instant lastViewedAt, boolean available) {
    }

    // Best attempt of each evaluation: its score, the topic it was taken with and its date.
    public record EvaluationResult(UUID evaluationId, String title, UUID categoryId, String topic, int bestScore,
                                   Instant attemptedAt, int attempts, boolean available) {
    }
}
