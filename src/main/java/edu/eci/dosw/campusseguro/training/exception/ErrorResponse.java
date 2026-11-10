package edu.eci.dosw.campusseguro.training.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// Common error format of the course (same as MS01). JSON names are fixed by the integration standards.
@Schema(type = "object", requiredProperties = {"status", "codigo", "mensaje", "errores", "correlation_id"})
public record ErrorResponse(
        int status,
        @JsonProperty("codigo") String code,
        @JsonProperty("mensaje") String message,
        @JsonProperty("errores") List<FieldError> errors,
        @JsonProperty("correlation_id") String correlationId) {

    public record FieldError(
            @JsonProperty("campo") String field,
            @JsonProperty("mensaje") String message) {
    }
}
