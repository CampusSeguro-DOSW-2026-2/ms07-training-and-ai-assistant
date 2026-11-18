package edu.eci.dosw.campusseguro.training.exception;

// 400 VALIDACION for checks done outside bean validation (query parameters, headers).
// Example: new BadRequestException("size", "El tamaño de página debe estar entre 1 y 100.")
public class BadRequestException extends RuntimeException {
    private final String field;

    public BadRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
