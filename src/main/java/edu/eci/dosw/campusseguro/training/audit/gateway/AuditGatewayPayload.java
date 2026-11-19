package edu.eci.dosw.campusseguro.training.audit.gateway;

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
        Map<String, Object> atributos
) {
}
