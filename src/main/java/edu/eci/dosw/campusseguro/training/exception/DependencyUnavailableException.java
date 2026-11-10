package edu.eci.dosw.campusseguro.training.exception;

// 503 DEPENDENCIA_NO_DISPONIBLE. Used by the tutor (RF-15) when the AI API does not answer in time.
public class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException(String message) {
        super(message);
    }

    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
