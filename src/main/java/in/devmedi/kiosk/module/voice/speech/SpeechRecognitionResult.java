package in.devmedi.kiosk.module.voice.speech;

/**
 * Result of a speech-recognition attempt.
 *
 * <p><strong>Safety invariant:</strong> a transcript is only meaningful when
 * the status is {@link SpeechRecognitionStatus#TRANSCRIBED} or
 * {@link SpeechRecognitionStatus#EMPTY}. Failure, unsupported, and unavailable
 * results carry no transcript, because an invented or fallback transcript must
 * never be mistaken for patient speech.</p>
 *
 * @param status     the recognition outcome
 * @param transcript recognized text, or {@code null}/blank when the outcome is not {@code TRANSCRIBED}
 * @param language   BCP-47 language tag the recognition was attempted in
 * @param durationMs processing duration in milliseconds (best effort)
 */
public record SpeechRecognitionResult(SpeechRecognitionStatus status,
                                      String transcript,
                                      String language,
                                      long durationMs) {

    public SpeechRecognitionResult {
        if (status == null) {
            status = SpeechRecognitionStatus.UNAVAILABLE;
        }
    }

    public static SpeechRecognitionResult transcribed(String transcript, String language) {
        return new SpeechRecognitionResult(SpeechRecognitionStatus.TRANSCRIBED, transcript, language, 0L);
    }

    public static SpeechRecognitionResult empty(String language) {
        return new SpeechRecognitionResult(SpeechRecognitionStatus.EMPTY, "", language, 0L);
    }

    public static SpeechRecognitionResult unsupported(String language) {
        return new SpeechRecognitionResult(SpeechRecognitionStatus.UNSUPPORTED, null, language, 0L);
    }

    public static SpeechRecognitionResult unavailable(String language) {
        return new SpeechRecognitionResult(SpeechRecognitionStatus.UNAVAILABLE, null, language, 0L);
    }

    public static SpeechRecognitionResult failed(String language, long durationMs) {
        return new SpeechRecognitionResult(SpeechRecognitionStatus.FAILED, null, language, durationMs);
    }
}