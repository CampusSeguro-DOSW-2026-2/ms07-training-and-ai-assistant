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
    public static final String EVALUATION_CREATED = "EVALUACION_CREADA";
    public static final String EVALUATION_UPDATED = "EVALUACION_ACTUALIZADA";
    public static final String ATTEMPT_REGISTERED = "INTENTO_REGISTRADO";
    public static final String EVALUATION_DEACTIVATED = "EVALUACION_DESACTIVADA";
    public static final String EVALUATION_REACTIVATED = "EVALUACION_REACTIVADA";
    public static final String CATEGORY_CREATED = "CATEGORIA_CREADA";
    public static final String CATEGORY_UPDATED = "CATEGORIA_ACTUALIZADA";
    public static final String CATEGORY_DEACTIVATED = "CATEGORIA_DESACTIVADA";
    public static final String CATEGORY_REACTIVATED = "CATEGORIA_REACTIVADA";
    public static final String CONTENT_CREATED = "CONTENIDO_CREADO";
    public static final String CONTENT_UPDATED = "CONTENIDO_ACTUALIZADO";
    public static final String CONTENT_DEACTIVATED = "CONTENIDO_DESACTIVADO";
    public static final String CONTENT_REACTIVATED = "CONTENIDO_REACTIVADO";

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

    // Outcome of a tutor query (RF-15 step 8); null for other events.
    @Column(name = "result", length = 20)
    private String result;

    protected AuditEvent() {
    }

    public AuditEvent(UUID userId, String eventType, String correlationId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.eventType = eventType;
        this.occurredAt = Instant.now();
        this.correlationId = correlationId;
    }

    public AuditEvent(UUID userId, String eventType, String correlationId, String result) {
        this(userId, eventType, correlationId);
        this.result = result;
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

    public String getResult() {
        return result;
    }
}
