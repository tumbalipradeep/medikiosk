package in.devmedi.kiosk.module.ai.provider;

import in.devmedi.kiosk.module.ai.config.AiProviderProperties;
import in.devmedi.kiosk.module.ai.config.RestClientConfig;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiRequest;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiResponse;
import in.devmedi.kiosk.module.ai.conversation.ConversationTurn;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Base adapter for REST conversational LLMs. Handles the HTTP transport via
 * Spring {@link RestClient}, enforces short connect/read timeouts through a
 * JDK-based request factory, and maps 429/5xx/network failures onto typed
 * exceptions. Subclasses supply only the provider-specific request body
 * shape and response extraction.
 */
public abstract class AbstractClinicalAiProvider implements ClinicalAiProvider {

    /**
     * Generic documentation-scope instruction. It deliberately contains no clinical
     * logic: providers are text exchangers, not clinical decision makers.
     */
    private static final String SYSTEM_PROMPT =
            "You assist with documenting a structured health-history interview. "
                    + "Restate, summarize, or ask one clear clarifying question at a time. "
                    + "You must NOT make any diagnosis or prognosis, must NOT prescribe or "
                    + "recommend any treatment, and must NOT assess clinical safety. "
                    + "Never invent clinical facts. Answer strictly according to the requested output.";

    private static final int MAX_HISTORY_TURNS = 6;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final AiProviderProperties.ProviderSettings settings;
    private final String apiKey;
    private final RestClient restClient;

    protected AbstractClinicalAiProvider(String name, AiProviderProperties.ProviderSettings settings,
                                         String apiKey, RestClient.Builder restClientBuilder) {
        this.name = name;
        this.settings = settings;
        this.apiKey = apiKey;
        this.restClient = restClientBuilder
                .requestFactory(RestClientConfig.jdkRequestFactory(timeout()))
                .build();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isEnabled() {
        return settings != null
                && settings.isEnabled()
                && apiKey != null
                && !apiKey.isBlank();
    }

    protected AiProviderProperties.ProviderSettings settings() {
        return settings;
    }

    protected String apiKey() {
        return apiKey;
    }

    protected Duration timeout() {
        return Duration.ofMillis(settings == null ? 12_000 : settings.getTimeoutMs());
    }

    @Override
    public ClinicalAiResponse complete(ClinicalAiRequest request) throws AiProviderException {
        if (!isEnabled()) {
            throw new AiProviderException(name + " provider is not enabled (missing config or API key)");
        }
        String responseBody;
        try {
            RestClient.RequestBodySpec post = restClient.post()
                    .uri(endpointUri(settings.getBaseUrl(), settings.getModel()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(request));
            addAuthHeader((header, value) -> post.header(header, value), apiKey);
            responseBody = post.retrieve().body(String.class);
        } catch (HttpServerErrorException ex) {
            throw new AiProviderServerException(name, ex.getStatusCode().value());
        } catch (HttpClientErrorException ex) {
            throw mapClientError(ex);
        } catch (ResourceAccessException ex) {
            throw new AiProviderTimeoutException(name, timeout(), ex);
        }

        String content = extractCompletion(responseBody);
        if (content == null || content.isBlank()) {
            throw new AiProviderException(name + " returned an empty completion");
        }
        return ClinicalAiResponse.of(name, settings.getModel(), content.trim(), request.getLanguage());
    }

    private AiProviderException mapClientError(HttpClientErrorException ex) {
        if (ex.getStatusCode().value() == 429) {
            return new AiProviderRateLimitedException(name, retryAfterSeconds(ex));
        }
        return new AiProviderException(name + " returned unexpected HTTP " + ex.getStatusCode().value(), ex);
    }

    private int retryAfterSeconds(HttpClientErrorException ex) {
        String header = ex.getResponseHeaders().getFirst("Retry-After");
        if (header == null || header.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(header.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Request body JSON, provider-specific.
     */
    protected abstract String buildRequestBody(ClinicalAiRequest request);

    /**
     * Extract free-text completion from the provider response JSON.
     */
    protected abstract String extractCompletion(String responseBody) throws AiProviderException;

    /**
     * Target endpoint (defaults to OpenAI-compatible chat completions).
     */
    protected String endpointUri(String baseUrl, String model) {
        return baseUrl + "/chat/completions";
    }

    /**
     * Attaches provider authentication. Default: Bearer header.
     */
    protected void addAuthHeader(BiConsumer<String, String> headerAdder, String apiKey) {
        headerAdder.accept("Authorization", "Bearer " + apiKey);
    }

    // ---------- shared payload helpers ----------

    protected String openAiCompletionsBody(ClinicalAiRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (ConversationTurn turn : tail(request.getConversation())) {
            messages.add(Map.of("role", "user", "content", turn.question()));
            messages.add(Map.of("role", "assistant", "content", turn.answer()));
        }
        messages.add(Map.of("role", "user", "content", userPrompt(request)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.getModel());
        body.put("messages", messages);
        body.put("max_tokens", settings.getMaxTokens());
        return mapJson(body);
    }

    protected String parseOpenAiCompletion(String responseBody) throws AiProviderException {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (tools.jackson.core.JacksonException ex) {
            throw new AiProviderException(name + " returned unparseable response", ex);
        }
    }

    protected String geminiGenerateContentBody(ClinicalAiRequest request) {
        Map<String, Object> systemInstruction = Map.of(
                "parts", List.of(Map.of("text", SYSTEM_PROMPT)));
        Map<String, Object> contents = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", historyPrompt(request))));
        Map<String, Object> generationConfig = Map.of(
                "maxOutputTokens", settings.getMaxTokens());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", systemInstruction);
        body.put("contents", List.of(contents));
        body.put("generationConfig", generationConfig);
        return mapJson(body);
    }

    protected String parseGeminiCompletion(String responseBody) throws AiProviderException {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
        } catch (tools.jackson.core.JacksonException ex) {
            throw new AiProviderException(name + " returned unparseable response", ex);
        }
    }

    private String userPrompt(ClinicalAiRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getCurrentQuestion() != null && !request.getCurrentQuestion().isBlank()) {
            sb.append(request.getCurrentQuestion()).append("\n\n");
        }
        if (request.getPatientAnswer() != null && !request.getPatientAnswer().isBlank()) {
            sb.append("Patient's answer: ").append(request.getPatientAnswer()).append("\n\n");
        }
        sb.append("Requested output: ").append(request.getRequestedResponse());
        return sb.toString();
    }

    private String historyPrompt(ClinicalAiRequest request) {
        StringBuilder sb = new StringBuilder();
        for (ConversationTurn turn : tail(request.getConversation())) {
            sb.append("Q: ").append(turn.question()).append('\n')
              .append("A: ").append(turn.answer()).append("\n\n");
        }
        return sb.append(userPrompt(request)).toString();
    }

    private List<ConversationTurn> tail(List<ConversationTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, turns.size() - MAX_HISTORY_TURNS);
        return turns.subList(from, turns.size());
    }

    private String mapJson(Map<String, Object> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (tools.jackson.core.JacksonException ex) {
            throw new AiProviderException(name + " failed to serialize request", ex);
        }
    }
}