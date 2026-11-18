package edu.eci.dosw.campusseguro.training.evaluation;

import edu.eci.dosw.campusseguro.training.security.Permissions;
import edu.eci.dosw.campusseguro.training.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// RF-14, management by ADMIN. Access rules live in SecurityConfig; @RequiresPermission documents them in the local swagger.
@RestController
@RequestMapping("/api/v1/training/evaluations")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public ResponseEntity<EvaluationResponse> create(@Valid @RequestBody EvaluationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(EvaluationResponse.from(service.create(request)));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public EvaluationResponse update(@PathVariable UUID id, @Valid @RequestBody EvaluationRequest request) {
        return EvaluationResponse.from(service.update(id, request));
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
