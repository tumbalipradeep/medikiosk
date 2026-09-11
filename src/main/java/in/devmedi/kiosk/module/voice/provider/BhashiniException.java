package in.devmedi.kiosk.module.voice.provider;

/**
 * Exception carrying the {@link BhashiniFailure} classification of an attempted
 * Bhashini/ULCA call. The failure kind lets callers map failures to
 * semantically correct public statuses (unavailable vs failed).
 */
public class BhashiniException extends RuntimeException {

    private final BhashiniFailure failure;

    public BhashiniException(BhashiniFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public BhashiniException(BhashiniFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public BhashiniFailure failure() {
        return failure;
    }
}