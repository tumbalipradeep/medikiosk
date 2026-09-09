package in.devmedi.kiosk.module.ai.provider;

/**
 * Raised when a provider returns an HTTP 5xx server error.
 */
public class AiProviderServerException extends AiProviderException {

    private final String provider;
    private final int status;

    public AiProviderServerException(String provider, int status) {
        super(provider + " server error (HTTP " + status + ")");
        this.provider = provider;
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public int getStatus() {
        return status;
    }
}