package in.devmedi.kiosk.module.voice.speech;

/**
 * Structured input for one text-to-speech synthesis attempt.
 *
 * @param text      the text to synthesize (patient-facing presentational text)
 * @param language  BCP-47 language tag the synthesis should speak in
 * @param sessionId stable identifier of the originating patient session, or
 *                  {@code null} when the context does not have one
 */
public record SpeechSynthesisRequest(String text, String language, String sessionId) {

    public SpeechSynthesisRequest {
        if (text == null) {
            text = "";
        }
        if (language == null) {
            language = "";
        }
    }
}