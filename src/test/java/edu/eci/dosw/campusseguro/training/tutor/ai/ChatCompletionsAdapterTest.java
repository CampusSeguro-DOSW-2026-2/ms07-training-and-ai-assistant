package edu.eci.dosw.campusseguro.training.tutor.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.eci.dosw.campusseguro.training.exception.DependencyUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The adapters against a local HTTP server that plays the provider: the real API is never called.
class ChatCompletionsAdapterTest {

    private static final String KEY = "test-key-123";
    private static final AiPrompt PROMPT = new AiPrompt("Responde solo con el contexto.", "¿Qué hago si tiembla?");
    private static final String ANSWER = """
            {"id":"x","object":"chat.completion","choices":[{"index":0,
            "message":{"role":"assistant","content":"  Agáchate, cúbrete y sujétate.  "},"finish_reason":"stop"}],
            "usage":{"total_tokens":10}}""";

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private final AtomicReference<Set<String>> headerNames = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private volatile String reply = ANSWER;
    private volatile long delayMs = 0;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/v1/chat/completions", this::handle);
        server.createContext("/v1/embeddings", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        apiKeyHeader.set(exchange.getRequestHeaders().getFirst("api-key"));
        headerNames.set(Set.copyOf(exchange.getRequestHeaders().keySet()));
        try {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (InterruptedException | IOException e) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    private EmbeddingProperties embedding() {
        return new EmbeddingProperties("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/embeddings",
                "text-embedding-test", Duration.ofSeconds(2));
    }

    private static final String VECTORS = """
            {"object":"list","data":[{"object":"embedding","index":1,"embedding":[0.5,0.25]},
            {"object":"embedding","index":0,"embedding":[0.1,-0.2]}],"model":"text-embedding-test"}""";

    @Test
    void openAiEmbeddingsSendTheModelAndKeepTheOrderOfTheTexts() {
        reply = VECTORS;
        OpenAiAdapter adapter = new OpenAiAdapter(properties("openai"), embedding());

        List<float[]> vectors = adapter.embed(List.of("hay fuga de gas", "se fue la luz"));

        assertThat(adapter.supportsEmbeddings()).isTrue();
        assertThat(adapter.embeddingModel()).isEqualTo("text-embedding-test");
        assertThat(vectors.get(0)).containsExactly(0.1f, -0.2f);
        assertThat(vectors.get(1)).containsExactly(0.5f, 0.25f);
        assertThat(authorization.get()).isEqualTo("Bearer " + KEY);
        assertThat(body.get()).contains("\"model\":\"text-embedding-test\"")
                .contains("\"input\":[\"hay fuga de gas\",\"se fue la luz\"]")
                .doesNotContain("\"user\"");
    }

    @Test
    void azureEmbeddingsTakeTheModelFromTheDeployment() {
        reply = VECTORS;

        new AzureOpenAiAdapter(properties("azure"), embedding()).embed(List.of("a", "b"));

        assertThat(apiKeyHeader.get()).isEqualTo(KEY);
        assertThat(body.get()).doesNotContain("\"model\"");
    }

    @Test
    void embeddingsWithMissingVectorsAreUnavailable() {
        reply = "{\"data\":[{\"index\":0,\"embedding\":[0.1]}]}";

        assertThatThrownBy(() -> new OpenAiAdapter(properties("openai"), embedding()).embed(List.of("a", "b")))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void embeddingsErrorsAreUnavailable() {
        status = 500;
        reply = "{}";

        assertThatThrownBy(() -> new OpenAiAdapter(properties("openai"), embedding()).embed(List.of("a")))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessage(ChatCompletionsAdapter.UNAVAILABLE);
    }

    @Test
    void withoutKeyOrUrlThereAreNoEmbeddings() {
        assertThat(new OpenAiAdapter(properties("openai", " ", Duration.ofSeconds(1)), embedding()).supportsEmbeddings())
                .isFalse();
        assertThat(new OpenAiAdapter(properties("openai")).supportsEmbeddings()).isFalse();
        assertThat(new EmbeddingProperties(null, null, null).model()).isEqualTo("text-embedding-3-small");
    }

    private TutorProperties properties(String provider, String key, Duration timeout) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
        return new TutorProperties(provider, url, key, "gpt-test", timeout, 200, 0.2);
    }

    private TutorProperties properties(String provider) {
        return properties(provider, KEY, Duration.ofSeconds(2));
    }

    @Test
    void openAiSendsBearerKeyModelAndBothMessages() {
        String text = new OpenAiAdapter(properties("openai")).complete(PROMPT);

        assertThat(text).isEqualTo("Agáchate, cúbrete y sujétate.");
        assertThat(authorization.get()).isEqualTo("Bearer " + KEY);
        assertThat(apiKeyHeader.get()).isNull();
        assertThat(body.get())
                .contains("\"model\":\"gpt-test\"")
                .contains("\"role\":\"system\"").contains("Responde solo con el contexto.")
                .contains("\"role\":\"user\"").contains("¿Qué hago si tiembla?")
                .contains("\"max_tokens\":200");
    }

    @Test
    void azureSendsApiKeyHeaderAndNoModel() {
        String text = new AzureOpenAiAdapter(properties("azure")).complete(PROMPT);

        assertThat(text).isEqualTo("Agáchate, cúbrete y sujétate.");
        assertThat(apiKeyHeader.get()).isEqualTo(KEY);
        assertThat(authorization.get()).isNull();
        assertThat(body.get()).doesNotContain("\"model\"").doesNotContain("\"store\"");
    }

    @Test
    void thePromptCanAskForJsonMoreTokensAndMoreTime() {
        delayMs = 500;
        OpenAiAdapter adapter = new OpenAiAdapter(properties("openai", KEY, Duration.ofMillis(200)));

        String text = adapter.complete(new AiPrompt("Responde en JSON.", "Genera 5 preguntas.", 1800,
                Duration.ofSeconds(3), true));

        assertThat(text).isEqualTo("Agáchate, cúbrete y sujétate.");
        assertThat(body.get())
                .contains("\"max_tokens\":1800")
                .contains("\"response_format\":{\"type\":\"json_object\"}");
    }

    @Test
    void theTutorKeepsItsOwnTimeoutAndNoJsonMode() {
        delayMs = 500;
        OpenAiAdapter adapter = new OpenAiAdapter(properties("openai", KEY, Duration.ofMillis(200)));
        adapter.complete(new AiPrompt("x", "y", null, Duration.ofSeconds(3), true));

        assertThatThrownBy(() -> adapter.complete(PROMPT)).isInstanceOf(DependencyUnavailableException.class);
        assertThat(body.get()).doesNotContain("response_format").contains("\"max_tokens\":200");
    }

    @Test
    void openAiIsAskedNotToStoreTheConversation() {
        new OpenAiAdapter(properties("openai")).complete(PROMPT);

        assertThat(body.get()).contains("\"store\":false");
    }

    @Test
    void theRequestSaysNothingAboutWhoAsks() {
        String correlationId = "4f1c2b3a-1111-4222-8333-944455556666";
        MDC.put("correlationId", correlationId);
        try {
            new OpenAiAdapter(properties("openai")).complete(PROMPT);
        } finally {
            MDC.remove("correlationId");
        }

        Map<String, Object> sent = JsonMapper.builder().build().readValue(body.get(), new TypeReference<>() {
        });
        assertThat(sent).containsOnlyKeys("model", "messages", "temperature", "max_tokens", "store");
        assertThat(body.get()).doesNotContain(correlationId);
        assertThat(headerNames.get())
                .noneMatch(name -> name.toLowerCase(Locale.ROOT).startsWith("x-user"))
                .noneMatch(name -> name.equalsIgnoreCase("X-Correlation-ID"));
    }

    @Test
    void providerErrorIsUnavailable() {
        status = 500;
        reply = "{\"error\":{\"message\":\"Incorrect API key provided: test-key-123\"}}";

        assertThatThrownBy(() -> new OpenAiAdapter(properties("openai")).complete(PROMPT))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessage(ChatCompletionsAdapter.UNAVAILABLE)
                .hasNoCause();
    }

    @Test
    void rejectedKeyIsUnavailable() {
        status = 401;
        reply = "{\"error\":{\"message\":\"invalid key\"}}";

        assertThatThrownBy(() -> new OpenAiAdapter(properties("openai")).complete(PROMPT))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void slowProviderTimesOut() {
        delayMs = 1500;
        OpenAiAdapter adapter = new OpenAiAdapter(properties("openai", KEY, Duration.ofMillis(300)));

        long start = System.nanoTime();

        assertThatThrownBy(() -> adapter.complete(PROMPT))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessage(ChatCompletionsAdapter.UNAVAILABLE)
                .hasNoCause();

        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isLessThan(Duration.ofMillis(1400));

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void answerWithoutTextIsUnavailable() {
        reply = "{\"choices\":[]}";

        assertThatThrownBy(() -> new OpenAiAdapter(properties("openai")).complete(PROMPT))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void answerThatIsNotJsonIsUnavailable() {
        reply = "<html>bad gateway</html>";

        assertThatThrownBy(() -> new OpenAiAdapter(properties("openai")).complete(PROMPT))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void missingKeyDoesNotCallTheProvider() {
        OpenAiAdapter adapter = new OpenAiAdapter(properties("openai", "  ", Duration.ofSeconds(2)));

        assertThatThrownBy(() -> adapter.complete(PROMPT)).isInstanceOf(DependencyUnavailableException.class);
        assertThat(calls.get()).isZero();
    }

    @Test
    void propertiesNeverPrintTheKey() {
        assertThat(properties("openai").toString()).doesNotContain(KEY);
    }

    @Test
    void propertiesHaveDefaults() {
        TutorProperties defaults = new TutorProperties(null, null, null, null, null, null, null);

        assertThat(defaults.provider()).isEqualTo("openai");
        assertThat(defaults.timeout()).isEqualTo(TutorProperties.DEFAULT_TIMEOUT);
        assertThat(defaults.apiKey()).isEmpty();
    }

    @Test
    void selectsTheAdapterNamedInProvider() {
        TutorProperties azure = properties(" Azure ");
        AiAdapters adapters = new AiAdapters(List.of(new OpenAiAdapter(azure), new AzureOpenAiAdapter(azure)), azure);

        assertThat(adapters.current()).isInstanceOf(AzureOpenAiAdapter.class);
    }

    @Test
    void unknownProviderFailsAtStartup() {
        TutorProperties gemini = properties("gemini");

        assertThatThrownBy(() -> new AiAdapters(List.of(new OpenAiAdapter(gemini)), gemini))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gemini")
                .hasMessageContaining("openai");
    }
}
