package in.devmedi.kiosk.module.ai.provider;

import in.devmedi.kiosk.module.ai.config.AiProviderProperties;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiRequest;
import org.springframework.web.client.RestClient;

import java.util.function.BiConsumer;

/**
 * Gemini adapter — Google Generative Language API (generateContent).
 * Uses {@code ?key=} query authentication instead of a Bearer header.
 */
public class GeminiProvider extends AbstractClinicalAiProvider {

    public static final String NAME = "gemini";

    public GeminiProvider(AiProviderProperties.ProviderSettings settings, String apiKey, RestClient.Builder restClientBuilder) {
        super(NAME, settings, apiKey, restClientBuilder);
    }

    @Override
    protected String endpointUri(String baseUrl, String model) {
        return baseUrl + "/models/" + model + ":generateContent?key=" + apiKey();
    }

    @Override
    protected void addAuthHeader(BiConsumer<String, String> headerAdder, String apiKey) {
        // Gemini authenticates via the key query parameter; no header needed.
    }

    @Override
    protected String buildRequestBody(ClinicalAiRequest request) {
        return geminiGenerateContentBody(request);
    }

    @Override
    protected String extractCompletion(String responseBody) throws AiProviderException {
        return parseGeminiCompletion(responseBody);
    }
}