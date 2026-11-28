package edu.eci.dosw.campusseguro.training.audit;

// RF-15 rule 5: the only outcome stored for a tutor query. Values are stored as they are, in Spanish.
// BLOQUEADA: the moderation stopped the question (a crisis or a topic that is not allowed); the text is never stored.
public enum TutorQueryResult {
    RESPONDIDA,
    FUERA_DE_ALCANCE,
    BLOQUEADA
}
