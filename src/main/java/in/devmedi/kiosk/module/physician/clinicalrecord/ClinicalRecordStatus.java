package in.devmedi.kiosk.module.physician.clinicalrecord;

/**
 * Status of a physician clinical summary section or the overall consultation.
 */
public enum ClinicalRecordStatus {
    /** Being edited; not yet final. */
    DRAFT,
    /** Explicitly accepted / final by the physician. */
    ACCEPTED,
    /** Explicitly rejected by the physician (AI content never auto-accepted). */
    REJECTED,
    /** Only valid for the whole consultation: finalized clinical record. */
    FINALIZED;

    public static ClinicalRecordStatus normalize(String value) {
        if (value == null) {
            return DRAFT;
        }
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (ClinicalRecordStatus s : values()) {
            if (s.name().equals(v)) {
                return s;
            }
        }
        return DRAFT;
    }
}