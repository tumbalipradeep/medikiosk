package in.devmedi.kiosk.module.auth.entity;

/**
 * Lifecycle status of a completed clinical case within the physician review
 * workflow. The default on persisted rows is {@link #OPEN} (the intake just
 * completed, no physician has taken it up yet).
 */
public enum CaseStatus {

    /** Intake completed, awaiting physician assignment or pool pickup. */
    OPEN,

    /** At least one physician is actively reviewing the case. */
    IN_REVIEW,

    /** Clinical record finalized: physician wrote the consultation. */
    COMPLETED;

    public static CaseStatus normalize(String value) {
        if (value == null) {
            return OPEN;
        }
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (CaseStatus s : values()) {
            if (s.name().equals(v)) {
                return s;
            }
        }
        return OPEN;
    }
}