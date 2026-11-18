package edu.eci.dosw.campusseguro.training.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// RF-13: minimal audit metadata. Never stores prompts, answers or sensitive content.
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    public static final String TUTOR_QUERY = "TUTOR_QUERY";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    protected AuditEvent() {
    }

    public AuditEvent(UUID userId, String eventType, String correlationId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.eventType = eventType;
        this.occurredAt = Instant.now();
        this.correlationId = correlationId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
