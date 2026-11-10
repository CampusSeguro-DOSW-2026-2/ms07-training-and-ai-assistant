package edu.eci.dosw.campusseguro.training.exception;

// 409 CONFLICTO. When a field is given, it is also listed in "errores".
// Example: new ConflictException("name", "Ya existe una categoría con ese nombre.")
public class ConflictException extends RuntimeException {
    private final String field;

    public ConflictException(String message) {
        this(null, message);
    }

    public ConflictException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
