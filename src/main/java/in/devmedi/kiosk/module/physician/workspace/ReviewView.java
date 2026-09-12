package in.devmedi.kiosk.module.physician.workspace;

import java.time.Instant;

/**
 * The physician's review decision against one captured answer, or an empty
 * marker when no decision has been recorded yet.
 */
public record ReviewView(boolean present,
                         String decision,
                         String amendedText,
                         String rationale,
                         String reviewer,
                         Instant decidedAt) {

    public static ReviewView none() {
        return new ReviewView(false, null, null, null, null, null);
    }
}