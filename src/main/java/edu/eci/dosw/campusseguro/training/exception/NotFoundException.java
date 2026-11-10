package edu.eci.dosw.campusseguro.training.exception;

// 404 NO_ENCONTRADO. Example: new NotFoundException("La categoría no existe.")
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
