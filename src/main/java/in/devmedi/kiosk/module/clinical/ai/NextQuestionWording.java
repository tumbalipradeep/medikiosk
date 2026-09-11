package in.devmedi.kiosk.module.clinical.ai;

import in.devmedi.kiosk.module.ai.provider.AiProviderFailure;

/**
 * The accepted wording for the next deterministic clinical question.
 *
 * <p>Only the display text may differ between AI and deterministic sources; the
 * question id, section, type, and ordering always stay deterministic. When the
 * source is {@link NextQuestionSource#DETERMINISTIC_FALLBACK}, {@code text} is
 * exactly the canonical deterministic question text.</p>
 *
 * @param text            question text to show to the patient
 * @param source          whether the text came from a validated AI response or the fallback
 * @param provider        provider name used for an AI text (null when none succeeded)
 * @param model           provider model used (null when none succeeded)
 * @param failure         failure category when the AI path did not succeed (null on success)
 * @param rejectionReason stable rejection label when validation rejected AI output
 * @param durationMs      elapsed time of the AI attempt (including the deterministic fast path)
 */
public record NextQuestionWording(String text,
                                  NextQuestionSource source,
                                  String provider,
                                  String model,
                                  AiProviderFailure failure,
                                  String rejectionReason,
                                  long durationMs) {

    public static NextQuestionWording aiGenerated(String text, String provider, String model, long durationMs) {
        return new NextQuestionWording(text, NextQuestionSource.AI_GENERATED, provider, model, null, null, durationMs);
    }

    public static NextQuestionWording deterministicFallback(String text, AiProviderFailure failure,
                                                            String rejectionReason, long durationMs) {
        return new NextQuestionWording(text, NextQuestionSource.DETERMINISTIC_FALLBACK, null, null, failure, rejectionReason, durationMs);
    }
}