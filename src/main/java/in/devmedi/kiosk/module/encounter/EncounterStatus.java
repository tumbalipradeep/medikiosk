package in.devmedi.kiosk.module.encounter;

/**
 * Lifecycle of a patient encounter (a single visit/consultation).
 *
 * <ul>
 *   <li>{@link #IN_PROGRESS} — started, intake in progress; can be resumed.</li>
 *   <li>{@link #SUBMITTED} — intake completed and submitted for clinician review;
 *       the encounter carries a persisted completed-case id.</li>
 *   <li>{@link #UNDER_REVIEW} — a physician has taken the case into review.</li>
 *   <li>{@link #COMPLETED} — the clinical record was finalized.</li>
 *   <li>{@link #CANCELLED} — the patient abandoned without submission.</li>
 * </ul>
 */
public enum EncounterStatus {
    IN_PROGRESS,
    SUBMITTED,
    UNDER_REVIEW,
    COMPLETED,
    CANCELLED;

    public static EncounterStatus normalize(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (EncounterStatus s : values()) {
            if (s.name().equals(v)) {
                return s;
            }
        }
        return null;
    }
}