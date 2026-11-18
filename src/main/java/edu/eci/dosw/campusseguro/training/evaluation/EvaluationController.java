package edu.eci.dosw.campusseguro.training.evaluation;

import edu.eci.dosw.campusseguro.training.common.PageResponse;
import edu.eci.dosw.campusseguro.training.common.Pagination;
import edu.eci.dosw.campusseguro.training.security.CurrentUser;
import edu.eci.dosw.campusseguro.training.security.Permissions;
import edu.eci.dosw.campusseguro.training.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// RF-14. Access rules live in SecurityConfig; @RequiresPermission documents them in the local swagger.
@RestController
@RequestMapping("/api/v1/training/evaluations")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission(Permissions.QUIZ_TAKE)
    public PageResponse<EvaluationSummaryResponse> list(@RequestParam(required = false) UUID categoryId,
                                                        @RequestParam(defaultValue = "false") boolean includeInactive,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        // includeInactive only applies to ADMIN; it is ignored for other roles.
        boolean showInactive = includeInactive && CurrentUser.hasPermission(Permissions.CONTENT_MANAGE);
        var result = service.list(showInactive, categoryId, Pagination.of(page, size, Sort.by("title")))
                .map(EvaluationSummaryResponse::from);
        return PageResponse.of(result);
    }

    // The community never receives which option is correct.
    @GetMapping("/{id}")
    @RequiresPermission(Permissions.QUIZ_TAKE)
    public EvaluationResponse get(@PathVariable UUID id) {
        boolean canManage = CurrentUser.hasPermission(Permissions.CONTENT_MANAGE);
        return EvaluationResponse.from(service.getForTaker(id, canManage), canManage);
    }

    @PostMapping("/{id}/attempts")
    @RequiresPermission(Permissions.QUIZ_TAKE)
    public ResponseEntity<AttemptResponse> attempt(@PathVariable UUID id, @Valid @RequestBody AttemptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.attempt(id, CurrentUser.id(), request));
    }

    @PostMapping
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public ResponseEntity<EvaluationResponse> create(@Valid @RequestBody EvaluationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(EvaluationResponse.from(service.create(CurrentUser.id(), request)));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public EvaluationResponse update(@PathVariable UUID id, @Valid @RequestBody EvaluationRequest request) {
        return EvaluationResponse.from(service.update(id, CurrentUser.id(), request));
    }

    @PatchMapping("/{id}/deactivate")
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public EvaluationResponse deactivate(@PathVariable UUID id) {
        return EvaluationResponse.from(service.deactivate(id));
    }

    @PatchMapping("/{id}/reactivate")
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public EvaluationResponse reactivate(@PathVariable UUID id) {
        return EvaluationResponse.from(service.reactivate(id));
    }
}
