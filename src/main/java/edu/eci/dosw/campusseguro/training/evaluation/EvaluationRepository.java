package edu.eci.dosw.campusseguro.training.evaluation;

import edu.eci.dosw.campusseguro.training.common.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    Page<Evaluation> findByStatus(Status status, Pageable pageable);

    Page<Evaluation> findByStatusAndCategoryId(Status status, UUID categoryId, Pageable pageable);

    Page<Evaluation> findByCategoryId(UUID categoryId, Pageable pageable);
}
