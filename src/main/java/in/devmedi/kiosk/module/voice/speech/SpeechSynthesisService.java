package in.devmedi.kiosk.module.voice.speech;

/**
 * Provider-neutral text-to-speech abstraction for patient intake.
 *
 * <p>This interface is the only thing clinical code should depend on for TTS.
 * Provider-specific integration (Bhashini, ULCA, cloud vendors, or browser
 * demo adapters) plugs in behind it during later M3 phases. No provider detail
 * may leak into the clinical layer.</p>
 *
 * <p>Implementations must honour the {@link SpeechSynthesisResult} safety
 * invariant: never report audio for a non-success status, and never pretend a
 * synthesis was produced when synthesis is unavailable.</p>
 */
@FunctionalInterface
public interface SpeechSynthesisService {

    /**
     * Synthesizes speech for the given presentational text.
     *
     * @param request the text, expected language, and session context
     * @return the synthesis outcome; never {@code null}
     */
    SpeechSynthesisResult synthesize(SpeechSynthesisRequest request);
}