package in.devmedi.kiosk.module.ai.provider;

/**
 * Safe, internal categories for AI provider failures observed by the AI
 * conversation orchestration layer. These are used for observability and
 * decisioning only — a failure never changes clinical behavior, which always
 * falls back to the deterministic backbone.
 */
public enum AiProviderFailure {

    /** No provider is configured/enabled (no API keys, all cards disabled). */
    NO_PROVIDERS_AVAILABLE,

    /** Provider configured but the request could not be carried out at all. */
    UNAVAILABLE,

    /** Provider did not answer within the configured timeout. */
    TIMEOUT,

    /** Provider refused with HTTP 429 (rate limited / quota). */
    RATE_LIMITED,

    /** Provider returned an HTTP 5xx server error. */
    SERVER_ERROR,

    /** Provider answered with an empty completion. */
    EMPTY_RESPONSE,

    /** Provider output could not be decoded into the structured contract. */
    MALFORMED_RESPONSE,

    /** Structured output decoded but failed clinical validation. */
    VALIDATION_REJECTED,

    /** Any failure that does not map to a more specific category. */
    UNSPECIFIED_FAILURE;

    /**
     * Maps a throwable raised by the provider/failover layer onto a safe category.
     *
     * @param throwable failure observed (may be {@code null})
     * @return the matching {@link AiProviderFailure} category
     */
    public static AiProviderFailure classify(Throwable throwable) {
        if (throwable == null) {
            return UNSPECIFIED_FAILURE;
        }
        if (throwable instanceof AiProviderTimeoutException) {
            return TIMEOUT;
        }
        if (throwable instanceof AiProviderRateLimitedException) {
            return RATE_LIMITED;
        }
        if (throwable instanceof AiProviderServerException) {
            return SERVER_ERROR;
        }
        if (throwable instanceof AiProviderException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("empty completion")) {
                return EMPTY_RESPONSE;
            }
            return UNSPECIFIED_FAILURE;
        }
        return UNSPECIFIED_FAILURE;
    }
}