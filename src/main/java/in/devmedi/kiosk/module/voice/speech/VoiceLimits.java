package in.devmedi.kiosk.module.voice.speech;

/**
 * Hard limits applied at the ASR/TTS transport boundary, shared by the HTTP
 * controllers and the provider services so both agree on the same maximums.
 */
public final class VoiceLimits {

    /** Maximum accepted size of one uploaded ASR audio clip (bytes). */
    public static final int ASR_MAX_INPUT_BYTES = 10 * 1024 * 1024;

    /** Maximum length (characters) of one TTS text request. */
    public static final int TTS_MAX_TEXT_CHARS = 500;

    /** Maximum duration (milliseconds) a patient can record in one clip. */
    public static final int MIC_MAX_RECORDING_MS = 30_000;

    private VoiceLimits() {
    }
}