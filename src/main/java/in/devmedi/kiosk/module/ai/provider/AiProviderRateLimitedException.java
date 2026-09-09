package in.devmedi.kiosk.module.ai.provider;

/**
 * Raised when a provider returns HTTP 429 (rate limited / quota exceeded).
 */
public class AiProviderRateLimitedException extends AiProviderException {

    private final String provider;

    public AiProviderRateLimitedException(String provider, int retryAfterSeconds) {
        super(provider + " is rate limited (HTTP 429, retry-after=" + retryAfterSeconds + "s)");
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}