package edu.eci.dosw.campusseguro.training.tutor.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.eci.dosw.campusseguro.training.exception.DependencyUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private volatile String reply = ANSWER;
    private volatile long delayMs = 0;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/v1/chat/completions", this::handle);
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
        assertThat(body.get()).doesNotContain("\"model\"");
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
        assertThatThrownBy(() -> adapter.complete(PROMPT)).isInstanceOf(DependencyUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1400));
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
