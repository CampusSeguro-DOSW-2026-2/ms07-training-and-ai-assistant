package edu.eci.dosw.campusseguro.training.tutor.ai;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

// Azure OpenAI: "api-key" header; the model is the deployment in LLM_URL
// (https://<resource>.openai.azure.com/openai/deployments/<deployment>/chat/completions?api-version=...).
// LLM_PROVIDER=azure.
@Component
public class AzureOpenAiAdapter extends ChatCompletionsAdapter {

    public AzureOpenAiAdapter(TutorProperties properties) {
        super(properties);
    }

    @Override
    public String provider() {
        return "azure";
    }

    @Override
    protected void authenticate(HttpHeaders headers, String apiKey) {
        headers.set("api-key", apiKey);
    }

    @Override
    protected boolean sendsModel() {
        return false;
    }
}
