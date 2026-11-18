package edu.eci.dosw.campusseguro.training.category;

import edu.eci.dosw.campusseguro.training.common.Status;
import edu.eci.dosw.campusseguro.training.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.training.exception.ConflictException;
import edu.eci.dosw.campusseguro.training.exception.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// RF-11 rules. Other packages use requireActive to check a category before using it.
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final String NAME_TAKEN = "Ya existe una categoría con ese nombre.";

    private final CategoryRepository repository;

    public CategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    public Page<Category> list(boolean includeInactive, Pageable pageable) {
        return includeInactive
                ? repository.findAll(pageable)
                : repository.findByStatus(Status.ACTIVO, pageable);
    }

    public Category get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("La categoría no existe."));
    }

    // For contents and evaluations: the referenced category must exist and be active (422 otherwise).
    public Category requireActive(UUID id) {
        Category category = repository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("La categoría no existe."));
        if (!category.isActive()) {
            throw new BusinessRuleException("La categoría '" + category.getName() + "' está inactiva.");
        }
        return category;
    }

    @Transactional
    public Category create(CategoryRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("name", NAME_TAKEN);
        }
        return save(new Category(name, clean(request.description())));
    }

    @Transactional
    public Category update(UUID id, CategoryRequest request) {
        Category category = get(id);
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("name", NAME_TAKEN);
        }
        category.update(name, clean(request.description()));
        return save(category);
    }

    @Transactional
    public Category deactivate(UUID id) {
        Category category = get(id);
        if (!category.isActive()) {
            throw new ConflictException("La categoría ya está inactiva.");
        }
        category.deactivate();
        return category;
    }

    @Transactional
    public Category reactivate(UUID id) {
        Category category = get(id);
        if (category.isActive()) {
            throw new ConflictException("La categoría ya está activa.");
        }
        category.reactivate();
        return category;
    }

    // The unique index also protects against two requests creating the same name at once.
    private Category save(Category category) {
        try {
            return repository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("name", NAME_TAKEN);
        }
    }

    private static String clean(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }
}
