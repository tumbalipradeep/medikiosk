package in.devmedi.kiosk.module.ai.provider;

import in.devmedi.kiosk.module.ai.config.AiProviderProperties;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiRequest;
import org.springframework.web.client.RestClient;

/**
 * OpenRouter adapter — OpenAI-compatible chat completions API.
 */
public class OpenRouterProvider extends AbstractClinicalAiProvider {

    public static final String NAME = "openrouter";

    public OpenRouterProvider(AiProviderProperties.ProviderSettings settings, String apiKey, RestClient.Builder restClientBuilder) {
        super(NAME, settings, apiKey, restClientBuilder);
    }

    @Override
    protected String buildRequestBody(ClinicalAiRequest request) {
        return openAiCompletionsBody(request);
    }

    @Override
    protected String extractCompletion(String responseBody) throws AiProviderException {
        return parseOpenAiCompletion(responseBody);
    }
}