package edu.eci.dosw.campusseguro.training.content;

import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryService;
import edu.eci.dosw.campusseguro.training.common.Status;
import edu.eci.dosw.campusseguro.training.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.training.exception.ConflictException;
import edu.eci.dosw.campusseguro.training.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// RF-10 rules. Other packages use requireActive to check a content before using it (progress, tutor).
@Service
@Transactional(readOnly = true)
public class ContentService {

    private static final String NOT_FOUND = "El contenido no existe.";

    private final ContentRepository repository;
    private final CategoryService categoryService;

    public ContentService(ContentRepository repository, CategoryService categoryService) {
        this.repository = repository;
        this.categoryService = categoryService;
    }

    // Managers (includeInactive) see every content; the community only active contents of active categories.
    public Page<Content> list(boolean includeInactive, UUID categoryId, Pageable pageable) {
        if (includeInactive) {
            return categoryId == null
                    ? repository.findAll(pageable)
                    : repository.findByCategoryId(categoryId, pageable);
        }
        return categoryId == null
                ? repository.findByStatusAndCategoryStatus(Status.ACTIVO, Status.ACTIVO, pageable)
                : repository.findByStatusAndCategoryStatusAndCategoryId(Status.ACTIVO, Status.ACTIVO, categoryId,
                        pageable);
    }

    public Content get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    // A hidden content answers 404 to the community, as if it did not exist.
    public Content getForReader(UUID id, boolean canManage) {
        Content content = get(id);
        if (!canManage && !content.isVisibleToCommunity()) {
            throw new NotFoundException(NOT_FOUND);
        }
        return content;
    }

    // For progress (content views) and other packages: 404 if missing, 422 if hidden.
    public Content requireActive(UUID id) {
        Content content = get(id);
        if (!content.isVisibleToCommunity()) {
            throw new BusinessRuleException("El contenido no está disponible.");
        }
        return content;
    }

    @Transactional
    public Content create(ContentRequest request) {
        Category category = categoryService.requireActive(request.categoryId());
        return repository.save(new Content(request.title().trim(), request.body().trim(), category));
    }

    // The current category can be kept even if it is now inactive; a new one must be active.
    @Transactional
    public Content update(UUID id, ContentRequest request) {
        Content content = get(id);
        Category category = content.getCategory().getId().equals(request.categoryId())
                ? content.getCategory()
                : categoryService.requireActive(request.categoryId());
        content.update(request.title().trim(), request.body().trim(), category);
        return content;
    }

    @Transactional
    public Content deactivate(UUID id) {
        Content content = get(id);
        if (!content.isActive()) {
            throw new ConflictException("El contenido ya está inactivo.");
        }
        content.deactivate();
        return content;
    }

    @Transactional
    public Content reactivate(UUID id) {
        Content content = get(id);
        if (content.isActive()) {
            throw new ConflictException("El contenido ya está activo.");
        }
        content.reactivate();
        return content;
    }
}
