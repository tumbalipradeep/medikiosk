package in.devmedi.kiosk.module.ai.provider;

/**
 * Base failure raised by an AI provider. The failover service treats every
 * subtype as a candidate for switching to the next provider.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}