package in.devmedi.kiosk.module.voice.speech;

/**
 * Structured input for one speech-recognition attempt.
 *
 * @param audio     the audio bytes to recognize (may be empty, never {@code null})
 * @param language  BCP-47 language tag the recognition should listen for
 * @param sessionId stable identifier of the originating patient session, or
 *                  {@code null} when the context does not have one
 */
public record SpeechRecognitionRequest(byte[] audio, String language, String sessionId) {

    public SpeechRecognitionRequest {
        if (audio == null) {
            audio = new byte[0];
        }
    }
}