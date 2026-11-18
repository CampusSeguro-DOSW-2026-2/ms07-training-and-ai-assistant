package edu.eci.dosw.campusseguro.training.evaluation;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.eci.dosw.campusseguro.training.common.Status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Full evaluation. "correct" is only filled for ADMIN; the community never receives it.
public record EvaluationResponse(UUID id, String title, String topic, UUID categoryId, Status status,
                                 List<QuestionResponse> questions, Instant createdAt, Instant updatedAt) {

    public record QuestionResponse(UUID id, int position, String statement, List<OptionResponse> options) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OptionResponse(UUID id, int position, String text, Boolean correct) {
    }

    public static EvaluationResponse from(Evaluation evaluation) {
        return from(evaluation, true);
    }

    public static EvaluationResponse from(Evaluation evaluation, boolean withAnswers) {
        List<QuestionResponse> questions = evaluation.getQuestions().stream()
                .map(q -> new QuestionResponse(q.getId(), q.getPosition(), q.getStatement(),
                        q.getOptions().stream()
                                .map(o -> new OptionResponse(o.getId(), o.getPosition(), o.getText(),
                                        withAnswers ? o.isCorrect() : null))
                                .toList()))
                .toList();
        return new EvaluationResponse(evaluation.getId(), evaluation.getTitle(), evaluation.getTopic(),
                evaluation.getCategory().getId(), evaluation.getStatus(), questions,
                evaluation.getCreatedAt(), evaluation.getUpdatedAt());
    }
}
