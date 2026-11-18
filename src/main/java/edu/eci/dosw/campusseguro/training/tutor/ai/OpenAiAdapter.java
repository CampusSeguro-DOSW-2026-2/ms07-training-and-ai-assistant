package edu.eci.dosw.campusseguro.training.tutor.ai;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

// OpenAI (api.openai.com): Bearer key and the model in the body. LLM_PROVIDER=openai.
@Component
public class OpenAiAdapter extends ChatCompletionsAdapter {

    public OpenAiAdapter(TutorProperties properties) {
        super(properties);
    }

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    protected void authenticate(HttpHeaders headers, String apiKey) {
        headers.setBearerAuth(apiKey);
    }

    @Override
    protected boolean sendsModel() {
        return true;
    }
}
