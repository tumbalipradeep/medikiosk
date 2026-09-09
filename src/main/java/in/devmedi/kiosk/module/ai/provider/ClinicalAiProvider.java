package in.devmedi.kiosk.module.ai.provider;

import in.devmedi.kiosk.module.ai.conversation.ClinicalAiRequest;
import in.devmedi.kiosk.module.ai.conversation.ClinicalAiResponse;

/**
 * A conversational AI provider for MediKiosk's clinical intake support.
 *
 * <p>This layer is deliberately independent of clinical business logic: providers
 * only exchange text with an external LLM. They never make diagnoses, never
 * determine clinical safety, and never evaluate patient answers medically.</p>
 */
public interface ClinicalAiProvider {

    /**
     * Stable, configuration-level name of the provider (e.g. "groq").
     */
    String getName();

    /**
     * True when the provider is configured and has an API key available at runtime.
     */
    boolean isEnabled();

    /**
     * Sends a conversational completion request and returns the requested response.
     *
     * @throws AiProviderException on any provider-level failure (network, HTTP error,
     *                             timeout, unparseable response).
     */
    ClinicalAiResponse complete(ClinicalAiRequest request) throws AiProviderException;
}