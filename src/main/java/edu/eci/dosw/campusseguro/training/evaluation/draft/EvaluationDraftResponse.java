package edu.eci.dosw.campusseguro.training.evaluation.draft;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.eci.dosw.campusseguro.training.evaluation.EvaluationRequest;

import java.util.UUID;

// State of a draft. draft has exactly the body of POST /api/v1/training/evaluations and is only there when LISTO;
// message (in Spanish) is only there when FALLIDO.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationDraftResponse(UUID id, DraftStatus status, EvaluationRequest draft, String message) {

    static EvaluationDraftResponse from(EvaluationDraftStore.Draft draft) {
        return new EvaluationDraftResponse(draft.id(), draft.status(), draft.result(), draft.message());
    }
}
