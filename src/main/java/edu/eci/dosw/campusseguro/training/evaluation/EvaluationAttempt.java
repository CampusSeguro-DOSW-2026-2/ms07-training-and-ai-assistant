package edu.eci.dosw.campusseguro.training.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// RF-14: one answered evaluation. Users can answer the same evaluation several times.
@Entity
@Table(name = "evaluation_attempt")
public class EvaluationAttempt {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private Evaluation evaluation;

    @Column(nullable = false, length = 150)
    private String topic;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private Instant attemptedAt;

    protected EvaluationAttempt() {
    }

    public EvaluationAttempt(UUID userId, Evaluation evaluation, int score) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.evaluation = evaluation;
        this.topic = evaluation.getTopic();
        this.score = score;
        this.attemptedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Evaluation getEvaluation() {
        return evaluation;
    }

    public String getTopic() {
        return topic;
    }

    public int getScore() {
        return score;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
