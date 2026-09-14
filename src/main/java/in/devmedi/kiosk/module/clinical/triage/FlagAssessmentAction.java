package in.devmedi.kiosk.module.clinical.triage;

/**
 * Action a physician took on a triage flag.
 */
public enum FlagAssessmentAction {
    /** Reviewed; no further escalation needed by this clinician. */
    SEEN,
    /** Escalated (e.g. urgent assessment, hospital transfer). */
    ESCALATED,
    /** Actively dismissed after assessment (never counted as a diagnosis). */
    CLEARED,
    /** Resolved during the consultation. */
    RESOLVED;

    public static FlagAssessmentAction normalize(String value) {
        if (value == null) {
            return SEEN;
        }
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (FlagAssessmentAction a : values()) {
            if (a.name().equals(v)) {
                return a;
            }
        }
        return SEEN;
    }

    /**
     * Strict parser used on the server boundary: returns {@code null} for any
     * unrecognized value instead of forgiving it to {@link #SEEN}.
     */
    public static FlagAssessmentAction parseStrict(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (FlagAssessmentAction a : values()) {
            if (a.name().equals(v)) {
                return a;
            }
        }
        return null;
    }
}