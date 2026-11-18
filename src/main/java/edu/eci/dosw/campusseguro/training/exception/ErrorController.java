package edu.eci.dosw.campusseguro.training.exception;

import edu.eci.dosw.campusseguro.training.config.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Answers errors that never reach a controller (e.g. raised by filters) with the ErrorResponse format.
@Hidden
@RestController
public class ErrorController implements org.springframework.boot.webmvc.error.ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        ErrorResponse body = new ErrorResponse(status.value(), codeFor(status), messageFor(status), List.of(),
                CorrelationIdFilter.from(request));
        return ResponseEntity.status(status).body(body);
    }

    private static HttpStatus resolveStatus(Object rawStatus) {
        HttpStatus status = rawStatus instanceof Integer value ? HttpStatus.resolve(value) : null;
        return status == null || status.is5xxServerError() ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }

    static String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "VALIDACION";
            case UNAUTHORIZED -> "NO_AUTENTICADO";
            case FORBIDDEN -> "PROHIBIDO";
            case NOT_FOUND -> "NO_ENCONTRADO";
            case METHOD_NOT_ALLOWED -> "METODO_NO_PERMITIDO";
            case UNSUPPORTED_MEDIA_TYPE -> "TIPO_NO_SOPORTADO";
            case CONFLICT -> "CONFLICTO";
            case UNPROCESSABLE_CONTENT -> "REGLA_NEGOCIO";
            default -> "ERROR_INTERNO";
        };
    }

    private static String messageFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "El recurso solicitado no existe.";
            case METHOD_NOT_ALLOWED -> "El método no está permitido en esta ruta.";
            case INTERNAL_SERVER_ERROR -> GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE;
            default -> "La solicitud no se pudo procesar.";
        };
    }
}
