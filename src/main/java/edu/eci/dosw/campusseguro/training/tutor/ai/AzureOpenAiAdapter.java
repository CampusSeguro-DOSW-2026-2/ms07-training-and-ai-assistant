package edu.eci.dosw.campusseguro.training.tutor.ai;

import org.springframework.beans.factory.annotation.Autowired;
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

    public AzureOpenAiAdapter(TutorProperties properties, EmbeddingProperties embedding) {
        super(properties, embedding);
    }

    @Autowired
    public AzureOpenAiAdapter(TutorProperties properties, EmbeddingProperties embedding, ModerationProperties moderation) {
        super(properties, embedding, moderation);
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

    // Not every Azure api-version accepts "store", and Azure does not keep chat completions unless asked to.
    // Azure OpenAI has no moderation endpoint: its own content filters apply to every request of the deployment.
    @Override
    public Moderation moderate(String text) {
        return Moderation.unavailable();
    }

    @Override
    protected boolean sendsStore() {
        return false;
    }
}
