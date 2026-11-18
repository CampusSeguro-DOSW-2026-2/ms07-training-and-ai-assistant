package edu.eci.dosw.campusseguro.training.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EvaluationAttemptRepository extends JpaRepository<EvaluationAttempt, UUID> {

    boolean existsByEvaluationId(UUID evaluationId);
}
