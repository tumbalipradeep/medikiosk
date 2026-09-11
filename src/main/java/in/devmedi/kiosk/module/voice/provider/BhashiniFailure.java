package in.devmedi.kiosk.module.voice.provider;

/**
 * Failure classification for one Bhashini/ULCA integration attempt. Kept coarse
 * so the service layer can map it to the conservative {@code SpeechRecognition}
 * / {@code SpeechSynthesis} statuses without leaking provider internals.
 */
public enum BhashiniFailure {

    /** Credentials are missing; the provider must be treated as unavailable. */
    UNCONFIGURED,

    /** The provider could not be reached (connection/transport error). */
    TRANSPORT,

    /** The provider did not answer within the configured timeout. */
    TIMEOUT,

    /** The provider rejected the request with HTTP 429. */
    RATE_LIMITED,

    /** The provider returned a server error (HTTP 5xx or unexpected 4xx). */
    SERVER,

    /** The provider answered, but the response did not match the ULCA contract. */
    MALFORMED
}