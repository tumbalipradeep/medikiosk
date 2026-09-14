package in.devmedi.kiosk.module.clinical.triage;

/**
 * Who noticed a flag.
 *
 * <ul>
 *   <li>{@link #PATIENT_REPORTED} — the patient's own words contain the concern
 *       (verbatim).</li>
 *   <li>{@link #SYSTEM_DETECTED} — the deterministic triage engine inferred it
 *       from the patient's words, from document content, or from structured
 *       data; it is a system-generated warning, never a diagnosis.</li>
 *   <li>{@link #PHYSICIAN_ASSESSMENT} — the physician recorded their own
 *       clinical assessment about the flag or about a new concern.</li>
 * </ul>
 */
public enum TriageSource {
    PATIENT_REPORTED,
    SYSTEM_DETECTED,
    PHYSICIAN_ASSESSMENT;

    public static TriageSource normalize(String value) {
        if (value == null) {
            return SYSTEM_DETECTED;
        }
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (TriageSource s : values()) {
            if (s.name().equals(v)) {
                return s;
            }
        }
        return SYSTEM_DETECTED;
    }
}