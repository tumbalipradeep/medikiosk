package in.devmedi.kiosk.module.voice.speech;

/**
 * Outcome of one text-to-speech (TTS) synthesis attempt.
 *
 * <p>Only {@link #SYNTHESIZED} carries synthesized audio. {@link #UNSUPPORTED},
 * {@link #UNAVAILABLE}, and {@link #FAILED} never imply that audio was
 * produced — a caller must never present a non-success result as speech.</p>
 */
public enum SpeechSynthesisStatus {

    /** Audio was successfully synthesized. */
    SYNTHESIZED,

    /** The requested language is not supported by the synthesis capability. */
    UNSUPPORTED,

    /** No synthesis capability is currently available. */
    UNAVAILABLE,

    /** The synthesis attempt failed. */
    FAILED
}