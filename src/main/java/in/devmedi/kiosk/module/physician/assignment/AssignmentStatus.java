package in.devmedi.kiosk.module.physician.assignment;

/**
 * State of a physician-to-case assignment.
 */
public enum AssignmentStatus {
    /** The physician is actively working the case. */
    ACTIVE,
    /** The physician finished their work on the case. */
    COMPLETED,
    /** The physician (or an admin) released the case back to the pool. */
    UNASSIGNED;

    public static AssignmentStatus normalize(String value) {
        if (value == null) {
            return ACTIVE;
        }
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (AssignmentStatus s : values()) {
            if (s.name().equals(v)) {
                return s;
            }
        }
        return ACTIVE;
    }
}