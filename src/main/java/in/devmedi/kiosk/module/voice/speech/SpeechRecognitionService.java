package in.devmedi.kiosk.module.voice.speech;

/**
 * Provider-neutral speech-to-text abstraction for patient intake.
 *
 * <p>This interface is the only thing clinical code should depend on for ASR.
 * Provider-specific integration (Bhashini, ULCA, cloud vendors, or browser
 * demo adapters) plugs in behind it during later M3 phases. No provider detail
 * may leak into the clinical layer.</p>
 *
 * <p>Implementations must honour the {@link SpeechRecognitionResult} safety
 * invariant: never return a transcript for a non-success status, and never
 * invent patient speech when recognition is unavailable.</p>
 */
@FunctionalInterface
public interface SpeechRecognitionService {

    /**
     * Transcribes the given audio.
     *
     * @param request the audio, expected language, and session context
     * @return the recognition outcome; never {@code null}
     */
    SpeechRecognitionResult transcribe(SpeechRecognitionRequest request);
}