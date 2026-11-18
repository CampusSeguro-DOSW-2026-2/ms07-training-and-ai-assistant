package edu.eci.dosw.campusseguro.training.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EvaluationAttemptRepository extends JpaRepository<EvaluationAttempt, UUID> {

    boolean existsByEvaluationId(UUID evaluationId);

    @Query("select a from EvaluationAttempt a join fetch a.evaluation where a.userId = :userId")
    List<EvaluationAttempt> findByUserIdWithEvaluation(@Param("userId") UUID userId);
}
