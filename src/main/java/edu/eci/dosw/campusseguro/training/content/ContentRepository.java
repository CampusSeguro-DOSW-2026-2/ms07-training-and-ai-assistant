package edu.eci.dosw.campusseguro.training.content;

import edu.eci.dosw.campusseguro.training.common.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// Separate derived queries instead of "(:categoryId is null or ...)": PostgreSQL cannot infer
// the type of a null UUID parameter.
public interface ContentRepository extends JpaRepository<Content, UUID> {

    Page<Content> findByCategoryId(UUID categoryId, Pageable pageable);

    Page<Content> findByStatusAndCategoryStatus(Status status, Status categoryStatus, Pageable pageable);

    Page<Content> findByStatusAndCategoryStatusAndCategoryId(Status status, Status categoryStatus, UUID categoryId,
                                                             Pageable pageable);
}
