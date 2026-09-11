package in.devmedi.kiosk.module.clinical.dialogue;

import java.util.Locale;

/**
 * How a patient answer entered the clinical pipeline.
 *
 * <p>The entry method is presentation-layer metadata only: the actual patient
 * answer is the raw text and never differs based on this value. Voice and
 * typed answers flow through the exact same clinical pipeline — same question
 * identity, ordering, progression, red-flag evaluation, and verbatim recording.
 * Unknown values normalize to {@link #TEXT} so nothing arbitrary is accepted.</p>
 */
public enum AnswerSource {

    /** The patient typed the answer. */
    TEXT,

    /** The answer was transcribed from patient speech. */
    VOICE;

    /**
     * Parses a client-supplied value, failing safe to {@link #TEXT}.
     *
     * @param value the raw value (may be {@code null} or blank)
     * @return {@link #VOICE} for {@code voice}, otherwise {@link #TEXT}
     */
    public static AnswerSource normalize(String value) {
        if (value != null && value.trim().toUpperCase(Locale.ROOT).equals("VOICE")) {
            return VOICE;
        }
        return TEXT;
    }
}