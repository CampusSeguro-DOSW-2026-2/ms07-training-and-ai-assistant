package edu.eci.dosw.campusseguro.training.exception;

import edu.eci.dosw.campusseguro.training.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Single error handler of MS07. Every error response uses the ErrorResponse format.
// Codes and messages stay in Spanish: codes are fixed by the standards and the front shows messages as-is.
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String INTERNAL_ERROR_MESSAGE = "Ocurrió un error inesperado. Intente de nuevo más tarde.";
    private static final String INVALID_DATA_MESSAGE = "Datos inválidos.";
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", e.getMessage(), List.of(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = e.getField() == null
                ? List.of()
                : List.of(new ErrorResponse.FieldError(e.getField(), e.getMessage()));
        return build(HttpStatus.CONFLICT, "CONFLICTO", e.getMessage(), errors, request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> businessRule(BusinessRuleException e, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, "REGLA_NEGOCIO", e.getMessage(), List.of(), request);
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    public ResponseEntity<ErrorResponse> dependencyUnavailable(DependencyUnavailableException e,
                                                               HttpServletRequest request) {
        log.warn("Dependency unavailable on {} {}: {}", request.getMethod(), request.getRequestURI(),
                e.getCause() != null ? e.getCause().toString() : e.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCIA_NO_DISPONIBLE", e.getMessage(), List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> byField = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> byField.putIfAbsent(f.getField(), f.getDefaultMessage()));
        List<ErrorResponse.FieldError> errors = byField.entrySet().stream()
                .map(entry -> new ErrorResponse.FieldError(entry.getKey(), entry.getValue()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", INVALID_DATA_MESSAGE, errors, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e,
                                                      HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors =
                List.of(new ErrorResponse.FieldError(e.getName(), "Valor con formato inválido."));
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", INVALID_DATA_MESSAGE, errors, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException e,
                                                          HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors =
                List.of(new ErrorResponse.FieldError(e.getParameterName(), "El parámetro es obligatorio."));
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", INVALID_DATA_MESSAGE, errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e,
                                                        HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDACION",
                "El cuerpo de la solicitud es inválido o tiene un valor no permitido.", List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> unknownRoute(NoResourceFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "El recurso solicitado no existe.", List.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException e,
                                                          HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO",
                "El método " + e.getMethod() + " no está permitido en esta ruta.", List.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> unsupportedMediaType(HttpMediaTypeNotSupportedException e,
                                                              HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_NO_SOPORTADO",
                "El tipo de contenido no es compatible; use application/json.", List.of(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> responseStatus(ResponseStatusException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        // Only codes from the catalog of the integration standards (section 7.2).
        String code = ErrorController.codeFor(status);
        String message = e.getReason() != null ? e.getReason() : status.getReasonPhrase();
        return build(status, code, message, List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> internalError(Exception e, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", INTERNAL_ERROR_MESSAGE, List.of(), request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                List<ErrorResponse.FieldError> errors,
                                                HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(status.value(), code, message, errors,
                CorrelationIdFilter.from(request));
        return ResponseEntity.status(status).body(body);
    }
}
