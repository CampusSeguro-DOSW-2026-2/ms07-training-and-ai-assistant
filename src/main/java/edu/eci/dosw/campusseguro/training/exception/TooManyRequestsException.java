package edu.eci.dosw.campusseguro.training.exception;

// 429 DEMASIADAS_SOLICITUDES: the user went over a usage limit (tutor questions, AI evaluation drafts).
// Example: new TooManyRequestsException("Hizo demasiadas preguntas seguidas. Espere un momento.")
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
