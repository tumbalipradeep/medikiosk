package in.devmedi.kiosk.module.ai.provider;

import java.time.Duration;

/**
 * Raised when a provider did not reply within the configured request timeout.
 */
public class AiProviderTimeoutException extends AiProviderException {

    private final String provider;
    private final Duration timeout;

    public AiProviderTimeoutException(String provider, Duration timeout, Throwable cause) {
        super(provider + " timed out after " + timeout, cause);
        this.provider = provider;
        this.timeout = timeout;
    }

    public String getProvider() {
        return provider;
    }

    public Duration getTimeout() {
        return timeout;
    }
}