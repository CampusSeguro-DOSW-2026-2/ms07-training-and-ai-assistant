package edu.eci.dosw.campusseguro.training.exception;

// 422 REGLA_NEGOCIO. Example: new BusinessRuleException("La categoría está inactiva.")
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
