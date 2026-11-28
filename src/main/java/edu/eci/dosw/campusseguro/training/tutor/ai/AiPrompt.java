package edu.eci.dosw.campusseguro.training.tutor.ai;

import java.time.Duration;

// What is sent to the model: the rules it must follow (system message) and the user message.
// maxTokens and timeout override LLM_MAX_TOKENS and LLM_TIMEOUT when they are not null; json asks the model for a
// JSON object (response_format json_object). The tutor uses the defaults; the evaluation drafts need more time.
public record AiPrompt(String instructions, String message, Integer maxTokens, Duration timeout, boolean json) {

    public AiPrompt(String instructions, String message) {
        this(instructions, message, null, null, false);
    }
}
