package in.devmedi.kiosk.module.ai.conversation;

/**
 * Immutable response produced by an AI provider. The caller (clinical engine,
 * arriving in a later checkpoint) interprets the free-text content.
 */
public final class ClinicalAiResponse {

    private final String provider;
    private final String model;
    private final String content;
    private final String language;

    public ClinicalAiResponse(String provider, String model, String content, String language) {
        this.provider = provider;
        this.model = model;
        this.content = content;
        this.language = language;
    }

    public static ClinicalAiResponse of(String provider, String model, String content, String language) {
        return new ClinicalAiResponse(provider, model, content, language);
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getContent() {
        return content;
    }

    public String getLanguage() {
        return language;
    }
}