package in.devmedi.kiosk.module.voice.speech;

/**
 * Outcome of one speech-recognition (ASR) attempt.
 *
 * <p>The statuses are deliberately conservative. A service must report exactly
 * one status; the only statuses that may carry {@link #transcript()} text are
 * {@link #TRANSCRIBED} and {@link #EMPTY} (the latter carries a blank string).
 * {@link #UNSUPPORTED}, {@link #UNAVAILABLE}, and {@link #FAILED} never carry a
 * transcript — a failed recognition attempt must never look like patient
 * speech.</p>
 */
public enum SpeechRecognitionStatus {

    /** Speech was recognized and a transcript is available. */
    TRANSCRIBED,

    /** Recognition ran but produced no audible speech; transcript is blank. */
    EMPTY,

    /** The requested language is not supported by the recognition capability. */
    UNSUPPORTED,

    /** No recognition capability is currently available. */
    UNAVAILABLE,

    /** The recognition attempt failed. */
    FAILED
}