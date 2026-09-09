package in.devmedi.kiosk.ai;

import com.sun.net.httpserver.HttpServer;
import in.devmedi.kiosk.module.ai.config.AiProviderProperties;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiRequest;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiResponse;
import in.devmedi.kiosk.module.ai.provider.AiProviderException;
import in.devmedi.kiosk.module.ai.provider.AiProviderRateLimitedException;
import in.devmedi.kiosk.module.ai.provider.AiProviderServerException;
import in.devmedi.kiosk.module.ai.provider.AiProviderTimeoutException;
import in.devmedi.kiosk.module.ai.provider.AbstractClinicalAiProvider;
import in.devmedi.kiosk.module.ai.provider.GeminiProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractClinicalAiProviderTests {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private int startEchoServer(AtomicReference<String> captured, String responseBody, int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            if (captured != null) {
                captured.set(exchange.getRequestURI().toString());
            }
            byte[] bytes = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();
        });
        server.start();
        return server.getAddress().getPort();
    }

    private int startSlowServer(long sleepMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException ignored) {
            }
            exchange.sendResponseHeaders(200, 2);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("ok".getBytes());
            }
            exchange.close();
        });
        server.start();
        return server.getAddress().getPort();
    }

    private AiProviderProperties.ProviderSettings settings(int port, long timeoutMs) {
        AiProviderProperties.ProviderSettings s = new AiProviderProperties.ProviderSettings();
        s.setEnabled(true);
        s.setModel("test-model");
        s.setBaseUrl("http://localhost:" + port);
        s.setTimeoutMs(timeoutMs);
        s.setMaxTokens(64);
        return s;
    }

    private ClinicalAiRequest request() {
        return ClinicalAiRequest.builder()
                .language("en")
                .currentQuestion("Describe your problem?")
                .patientAnswer("Headache.")
                .requestedResponse("CAPTURE_STATEMENT")
                .build();
    }

    static class StubProvider extends AbstractClinicalAiProvider {
        StubProvider(AiProviderProperties.ProviderSettings settings, String apiKey) {
            super("stub", settings, apiKey, RestClient.builder());
        }

        @Override
        protected String buildRequestBody(ClinicalAiRequest r) {
            return openAiCompletionsBody(r);
        }

        @Override
        protected String extractCompletion(String body) throws AiProviderException {
            return parseOpenAiCompletion(body);
        }
    }

    @Test
    void successReturnsParsedCompletion() throws IOException {
        int port = startEchoServer(null,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"A captured statement.\"}}]}", 200);
        StubProvider provider = new StubProvider(settings(port, 3000), "test-key");

        ClinicalAiResponse response = provider.complete(request());

        assertThat(response.getProvider()).isEqualTo("stub");
        assertThat(response.getModel()).isEqualTo("test-model");
        assertThat(response.getContent()).isEqualTo("A captured statement.");
    }

    @Test
    void http429RaisesRateLimitedException() throws IOException {
        int port = startEchoServer(null, "{\"error\":\"rate limit\"}", 429);
        StubProvider provider = new StubProvider(settings(port, 3000), "test-key");

        assertThatThrownBy(() -> provider.complete(request()))
                .isInstanceOf(AiProviderRateLimitedException.class)
                .hasMessageContaining("429");
    }

    @Test
    void http5xxRaisesServerException() throws IOException {
        int port = startEchoServer(null, "boom", 503);
        StubProvider provider = new StubProvider(settings(port, 3000), "test-key");

        assertThatThrownBy(() -> provider.complete(request()))
                .isInstanceOf(AiProviderServerException.class)
                .hasMessageContaining("503");
    }

    @Test
    void unexpectedHttp4xxRaisesGenericException() throws IOException {
        int port = startEchoServer(null, "bad request", 400);
        StubProvider provider = new StubProvider(settings(port, 3000), "test-key");

        assertThatThrownBy(() -> provider.complete(request()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("400");
    }

    @Test
    void timeoutRaisesTimeoutException() throws IOException {
        int port = startSlowServer(1500);
        StubProvider provider = new StubProvider(settings(port, 200), "test-key");

        assertThatThrownBy(() -> provider.complete(request()))
                .isInstanceOf(AiProviderTimeoutException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void missingApiKeyDisablesProvider() throws IOException {
        int port = startEchoServer(null, "{}", 200);
        StubProvider provider = new StubProvider(settings(port, 3000), null);

        assertThat(provider.isEnabled()).isFalse();
        assertThatThrownBy(() -> provider.complete(request()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void blankApiKeyDisablesProvider() throws IOException {
        int port = startEchoServer(null, "{}", 200);
        StubProvider provider = new StubProvider(settings(port, 3000), "  ");

        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void emptyCompletionRaisesException() throws IOException {
        int port = startEchoServer(null,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}]}", 200);
        StubProvider provider = new StubProvider(settings(port, 3000), "test-key");

        assertThatThrownBy(() -> provider.complete(request()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("empty completion");
    }

    @Test
    void unparseableBodyRaisesException() throws IOException {
        int port = startEchoServer(null, "not-json", 200);
        StubProvider provider = new StubProvider(settings(port, 3000), "test-key");

        assertThatThrownBy(() -> provider.complete(request()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("unparseable");
    }

    @Test
    void geminiAuthenticatesViaQueryKey() throws IOException {
        AtomicReference<String> captured = new AtomicReference<>();
        int port = startEchoServer(captured,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Gemini reply\"}]}}]}", 200);
        GeminiProvider provider = new GeminiProvider(settings(port, 3000), "GEM-KEY", RestClient.builder());

        ClinicalAiResponse response = provider.complete(request());

        assertThat(response.getContent()).isEqualTo("Gemini reply");
        assertThat(captured.get()).contains(":generateContent");
        assertThat(captured.get()).contains("key=GEM-KEY");
        assertThat(captured.get()).doesNotContain("Authorization");
    }
}