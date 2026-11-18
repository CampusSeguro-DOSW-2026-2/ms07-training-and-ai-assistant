package edu.eci.dosw.campusseguro.training.tutor.ai;

// What the tutor sends to the model: the rules it must follow (system message) and the user message.
public record AiPrompt(String instructions, String message) {
}
