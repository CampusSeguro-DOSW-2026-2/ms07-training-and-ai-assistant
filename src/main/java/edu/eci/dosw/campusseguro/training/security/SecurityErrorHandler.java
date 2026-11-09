package edu.eci.dosw.campusseguro.training.security;

import edu.eci.dosw.campusseguro.training.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Answers 401 and 403 with the same ErrorResponse format as the rest of the service.
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException e) throws IOException {
        write(request, response, HttpServletResponse.SC_UNAUTHORIZED, "NO_AUTENTICADO",
                "Faltan las cabeceras de identidad.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException e) throws IOException {
        write(request, response, HttpServletResponse.SC_FORBIDDEN, "PROHIBIDO",
                "No tiene permiso para realizar esta operación.");
    }

    // JSON keys are fixed by the integration standards (same as ErrorResponse).
    private void write(HttpServletRequest request, HttpServletResponse response, int status,
                       String code, String message) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\"status\":").append(status)
                .append(",\"codigo\":\"").append(code).append('"')
                .append(",\"mensaje\":\"").append(message).append('"')
                .append(",\"errores\":[]");
        json.append(",\"correlation_id\":\"").append(escape(CorrelationIdFilter.from(request))).append('"');
        json.append('}');

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json.toString());
    }

    private String escape(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
