package edu.eci.dosw.campusseguro.training.tutor;

// Body of POST /api/v1/tutor/questions. InputAnalyzer checks it (not empty, at most 500 characters).
public record TutorQuestionRequest(String question) {
}
