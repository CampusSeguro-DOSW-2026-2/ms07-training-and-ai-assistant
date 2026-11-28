package edu.eci.dosw.campusseguro.training.tutor.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import edu.eci.dosw.campusseguro.training.exception.DependencyUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Shared client for the "chat completions" API, used by OpenAI and Azure OpenAI.
// Each subclass only says how it authenticates and whether the body carries the model.
public abstract class ChatCompletionsAdapter implements AiAdapter {

    public static final String UNAVAILABLE =
            "El tutor no está disponible en este momento. Intenta de nuevo o consulta el centro de aprendizaje.";

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionsAdapter.class);

    private final TutorProperties properties;
    private final EmbeddingProperties embedding;
    // One client per timeout: LLM_TIMEOUT for the tutor, the longer one of the evaluation drafts and the short one
    // of the embeddings.
    private final Map<Duration, RestClient> clients = new ConcurrentHashMap<>();

    protected ChatCompletionsAdapter(TutorProperties properties) {
        this(properties, EmbeddingProperties.disabled());
    }

    protected ChatCompletionsAdapter(TutorProperties properties, EmbeddingProperties embedding) {
        this.properties = properties;
        this.embedding = embedding;
        client(properties.timeout());
    }

    @Override
    public boolean supportsEmbeddings() {
        return !properties.apiKey().isEmpty() && !embedding.url().isEmpty();
    }

    @Override
    public String embeddingModel() {
        return embedding.model();
    }

    // POST /v1/embeddings with every text at once; the answer keeps the order with "index".
    @Override
    public List<float[]> embed(List<String> texts) {
        if (!supportsEmbeddings()) {
            throw new DependencyUnavailableException(UNAVAILABLE);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (sendsModel()) {
            body.put("model", embedding.model());
        }
        body.put("input", texts);
        EmbeddingResponse response = post(embedding.url(), embedding.timeout(), body, EmbeddingResponse.class);
        if (response == null || response.data() == null || response.data().size() != texts.size()) {
            log.warn("AI provider {} answered embeddings without the expected vectors", provider());
            throw new DependencyUnavailableException(UNAVAILABLE);
        }
        float[][] vectors = new float[texts.size()][];
        for (EmbeddingResponse.Item item : response.data()) {
            if (item.index() < 0 || item.index() >= vectors.length || item.embedding() == null) {
                throw new DependencyUnavailableException(UNAVAILABLE);
            }
            vectors[item.index()] = item.embedding();
        }
        return List.of(vectors);
    }

    private RestClient client(Duration timeout) {
        return clients.computeIfAbsent(timeout, t -> {
            HttpClient http = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(t)
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
            factory.setReadTimeout(t);
            return RestClient.builder().requestFactory(factory).build();
        });
    }

    // Puts the API key in the header the provider expects.
    protected abstract void authenticate(HttpHeaders headers, String apiKey);

    // OpenAI needs the model in the body; Azure takes it from the deployment in the URL.
    protected abstract boolean sendsModel();

    // "store": false asks the provider not to keep the conversation. Only where the provider accepts the field.
    protected abstract boolean sendsStore();

    @Override
    public String complete(AiPrompt prompt) {
        if (properties.apiKey().isEmpty() || properties.url().isEmpty()) {
            log.warn("The tutor cannot call {}: LLM_API_KEY or LLM_URL is not set", provider());
            throw new DependencyUnavailableException(UNAVAILABLE);
        }
        Duration timeout = prompt.timeout() != null ? prompt.timeout() : properties.timeout();
        return textOf(post(properties.url(), timeout, body(prompt), ChatResponse.class));
    }

    // Every call to the provider: JSON body, the key in its header, and errors turned into 503 without details.
    protected <T> T post(String url, Duration timeout, Object body, Class<T> type) {
        try {
            return client(timeout).post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> authenticate(headers, properties.apiKey()))
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException e) {
            // Only the status: the provider body can echo part of the key.
            log.warn("AI provider {} answered HTTP {}", provider(), e.getStatusCode().value());
            throw new DependencyUnavailableException(UNAVAILABLE);
        } catch (RestClientException e) {
            // Timeout, connection or certificate error, or a body that is not the expected JSON.
            // Only the type of the root cause: its message can quote the answer of the model.
            log.warn("AI provider {} could not be used: {}", provider(),
                    NestedExceptionUtils.getMostSpecificCause(e).getClass().getSimpleName());
            throw new DependencyUnavailableException(UNAVAILABLE);
        }
    }

    // Only the model, the two messages and the generation settings: never a "user" field, the user id, the
    // X-User-* headers or the correlation id. Nothing in the request says who asked.
    private Map<String, Object> body(AiPrompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (sendsModel() && !properties.model().isEmpty()) {
            body.put("model", properties.model());
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", prompt.instructions()),
                Map.of("role", "user", "content", prompt.message())));
        body.put("temperature", properties.temperature());
        body.put("max_tokens", prompt.maxTokens() != null ? prompt.maxTokens() : properties.maxTokens());
        if (prompt.json()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        if (sendsStore()) {
            body.put("store", false);
        }
        return body;
    }

    private String textOf(ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null
                || response.choices().get(0).message().content().isBlank()) {
            log.warn("AI provider {} answered without text", provider());
            throw new DependencyUnavailableException(UNAVAILABLE);
        }
        return response.choices().get(0).message().content().trim();
    }

    // Only the fields read from an embeddings answer.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(List<Item> data) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Item(int index, float[] embedding) {
        }
    }

    // Only the fields the tutor reads from the provider answer.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Choice(Message message) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Message(String content) {
        }
    }
}
