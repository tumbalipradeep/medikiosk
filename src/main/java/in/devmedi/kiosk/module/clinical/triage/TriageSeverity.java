package in.devmedi.kiosk.module.clinical.triage;

/**
 * Severity of a triage flag.
 *
 * <p>Distinct from the legacy {@code RedFlagSeverity}: triage adds an explicit
 * intermediate severity so the system can distinguish ordinary concerns from
 * possible emergencies without pretending to triage. No flag, at any severity,
 * is ever a diagnosis.</p>
 */
public enum TriageSeverity {
    /** No concern. */
    NONE,
    /** Non-urgent but worth the clinician's attention. */
    WARNING,
    /** Possible emergency wording; requires urgent clinician assessment. */
    URGENT;

    public static TriageSeverity highest(TriageSeverity a, TriageSeverity b) {
        int ra = a == null ? 0 : a.ordinal();
        int rb = b == null ? 0 : b.ordinal();
        return ra >= rb ? a : b;
    }

    public static TriageSeverity normalize(String value) {
        if (value == null) {
            return NONE;
        }
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (TriageSeverity s : values()) {
            if (s.name().equals(v)) {
                return s;
            }
        }
        return NONE;
    }
}