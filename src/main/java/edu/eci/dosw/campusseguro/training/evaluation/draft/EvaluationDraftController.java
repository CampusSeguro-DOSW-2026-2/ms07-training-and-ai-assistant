package edu.eci.dosw.campusseguro.training.evaluation.draft;

import edu.eci.dosw.campusseguro.training.security.CurrentUser;
import edu.eci.dosw.campusseguro.training.security.Permissions;
import edu.eci.dosw.campusseguro.training.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// RF-14, drafts written by the AI. Access rules live in SecurityConfig; @RequiresPermission documents them.
@RestController
@RequestMapping("/api/v1/training/evaluation-drafts")
public class EvaluationDraftController {

    private final EvaluationDraftService service;

    public EvaluationDraftController(EvaluationDraftService service) {
        this.service = service;
    }

    // 202: the draft is being written; the client polls GET /{id}.
    @PostMapping
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public ResponseEntity<EvaluationDraftResponse> start(@Valid @RequestBody EvaluationDraftRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.start(CurrentUser.id(), request));
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public EvaluationDraftResponse get(@PathVariable UUID id) {
        return service.get(id, CurrentUser.id());
    }
}
