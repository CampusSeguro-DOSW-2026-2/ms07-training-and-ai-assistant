package edu.eci.dosw.campusseguro.training.content;

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

// RF-10 and RF-12. Access rules live in SecurityConfig; @RequiresPermission documents them in the local swagger.
@RestController
@RequestMapping("/api/v1/training/contents")
public class ContentController {

    private final ContentService service;

    public ContentController(ContentService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission(Permissions.CONTENT_READ)
    public PageResponse<ContentSummaryResponse> list(@RequestParam(required = false) UUID categoryId,
                                                     @RequestParam(defaultValue = "false") boolean includeInactive,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        // includeInactive only applies to ADMIN; it is ignored for other roles.
        boolean showInactive = includeInactive && CurrentUser.hasPermission(Permissions.CONTENT_MANAGE);
        var result = service.list(showInactive, categoryId,
                        Pagination.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(ContentSummaryResponse::from);
        return PageResponse.of(result);
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permissions.CONTENT_READ)
    public ContentResponse get(@PathVariable UUID id) {
        boolean canManage = CurrentUser.hasPermission(Permissions.CONTENT_MANAGE);
        return ContentResponse.from(service.getForReader(id, canManage));
    }

    @PostMapping
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public ResponseEntity<ContentResponse> create(@Valid @RequestBody ContentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ContentResponse.from(service.create(request)));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public ContentResponse update(@PathVariable UUID id, @Valid @RequestBody ContentRequest request) {
        return ContentResponse.from(service.update(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public ContentResponse deactivate(@PathVariable UUID id) {
        return ContentResponse.from(service.deactivate(id));
    }

    @PatchMapping("/{id}/reactivate")
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public ContentResponse reactivate(@PathVariable UUID id) {
        return ContentResponse.from(service.reactivate(id));
    }
}
