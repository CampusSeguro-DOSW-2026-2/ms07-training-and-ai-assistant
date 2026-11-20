package edu.eci.dosw.campusseguro.training.audit.gateway;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditGatewayPayload(
        UUID id,
        @JsonProperty("usuario_id") UUID userId,
        String modulo,
        String accion,
        @JsonProperty("recurso_afectado") String affectedResource,
        String resultado,
        @JsonProperty("fecha_hora") Instant occurredAt,
        @JsonProperty("correlation_id") String correlationId,
        String detalle,
        @JsonProperty("evento_id") UUID eventId,
        @JsonProperty("tipo_evento") String eventType,
        Map<String, Object> atributos,
        @JsonIgnore String userRole,
        @JsonIgnore String userPermission
) {
    // Keeps compatibility with existing tests and callers.
    public AuditGatewayPayload(
            UUID id,
            UUID userId,
            String modulo,
            String accion,
            String affectedResource,
            String resultado,
            Instant occurredAt,
            String correlationId,
            String detalle,
            UUID eventId,
            String eventType,
            Map<String, Object> atributos
    ) {
        this(id, userId, modulo, accion, affectedResource, resultado,
                occurredAt, correlationId, detalle, eventId, eventType,
                atributos, null, null);
    }
}
