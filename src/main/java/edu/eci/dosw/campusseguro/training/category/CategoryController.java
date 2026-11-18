package edu.eci.dosw.campusseguro.training.category;

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

// RF-11. Access rules live in SecurityConfig; @RequiresPermission documents them in the local swagger.
@RestController
@RequestMapping("/api/v1/training/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission(Permissions.CONTENT_READ)
    public PageResponse<CategoryResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        // includeInactive only applies to ADMIN; it is ignored for other roles.
        boolean showInactive = includeInactive && CurrentUser.hasPermission(Permissions.CONTENT_MANAGE);
        var result = service.list(showInactive, Pagination.of(page, size, Sort.by("name")))
                .map(CategoryResponse::from);
        return PageResponse.of(result);
    }

    @PostMapping
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.from(service.create(request)));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(service.update(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public CategoryResponse deactivate(@PathVariable UUID id) {
        return CategoryResponse.from(service.deactivate(id));
    }

    @PatchMapping("/{id}/reactivate")
    @RequiresPermission(Permissions.CONTENT_MANAGE)
    public CategoryResponse reactivate(@PathVariable UUID id) {
        return CategoryResponse.from(service.reactivate(id));
    }
}
