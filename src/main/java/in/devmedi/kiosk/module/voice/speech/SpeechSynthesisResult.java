package in.devmedi.kiosk.module.voice.speech;

/**
 * Result of a text-to-speech synthesis attempt.
 *
 * <p><strong>Safety invariant:</strong> synthesized audio is only meaningful
 * when the status is {@link SpeechSynthesisStatus#SYNTHESIZED}. Unsupported,
 * unavailable, and failed results carry no audio — a fallback must never
 * pretend audio was generated.</p>
 *
 * @param status     the synthesis outcome
 * @param audio      synthesized audio bytes, empty when not synthesized
 * @param language   BCP-47 language tag the synthesis was attempted in
 * @param durationMs synthesis duration in milliseconds (best effort)
 */
public record SpeechSynthesisResult(SpeechSynthesisStatus status,
                                    byte[] audio,
                                    String language,
                                    long durationMs) {

    public SpeechSynthesisResult {
        if (status == null) {
            status = SpeechSynthesisStatus.UNAVAILABLE;
        }
        if (audio == null) {
            audio = new byte[0];
        }
    }

    public static SpeechSynthesisResult synthesized(byte[] audio, String language) {
        return new SpeechSynthesisResult(SpeechSynthesisStatus.SYNTHESIZED, audio, language, 0L);
    }

    public static SpeechSynthesisResult unsupported(String language) {
        return new SpeechSynthesisResult(SpeechSynthesisStatus.UNSUPPORTED, new byte[0], language, 0L);
    }

    public static SpeechSynthesisResult unavailable(String language) {
        return new SpeechSynthesisResult(SpeechSynthesisStatus.UNAVAILABLE, new byte[0], language, 0L);
    }

    public static SpeechSynthesisResult failed(String language, long durationMs) {
        return new SpeechSynthesisResult(SpeechSynthesisStatus.FAILED, new byte[0], language, durationMs);
    }
}