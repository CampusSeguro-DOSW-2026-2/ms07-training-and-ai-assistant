package edu.eci.dosw.campusseguro.training.evaluation;

import edu.eci.dosw.campusseguro.training.common.Status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Management view: it includes which option is correct, so it is only returned to ADMIN.
public record EvaluationResponse(UUID id, String title, String topic, UUID categoryId, Status status,
                                 List<QuestionResponse> questions, Instant createdAt, Instant updatedAt) {

    public record QuestionResponse(UUID id, int position, String statement, List<OptionResponse> options) {
    }

    public record OptionResponse(UUID id, int position, String text, boolean correct) {
    }

    public static EvaluationResponse from(Evaluation evaluation) {
        List<QuestionResponse> questions = evaluation.getQuestions().stream()
                .map(q -> new QuestionResponse(q.getId(), q.getPosition(), q.getStatement(),
                        q.getOptions().stream()
                                .map(o -> new OptionResponse(o.getId(), o.getPosition(), o.getText(), o.isCorrect()))
                                .toList()))
                .toList();
        return new EvaluationResponse(evaluation.getId(), evaluation.getTitle(), evaluation.getTopic(),
                evaluation.getCategory().getId(), evaluation.getStatus(), questions,
                evaluation.getCreatedAt(), evaluation.getUpdatedAt());
    }
}
