package in.devmedi.kiosk.module.medication.enums;

/**
 * Severity of a screened medication interaction. Ordering is fixed for the
 * overall-case summary; CONTRAINDICATED dominates everything else.
 */
public enum MedicationInteractionSeverity {

    MINOR,
    MODERATE,
    MAJOR,
    CONTRAINDICATED;

    /**
     * @return the more severe of the two given levels (never null)
     */
    public static MedicationInteractionSeverity highest(MedicationInteractionSeverity a,
                                                       MedicationInteractionSeverity b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static MedicationInteractionSeverity parseStrict(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (MedicationInteractionSeverity level : values()) {
            if (level.name().equalsIgnoreCase(value.trim())) {
                return level;
            }
        }
        throw new IllegalArgumentException(String.format(
                "Unknown interaction severity '%s' (expected one of %s)", value, java.util.Arrays.toString(values())));
    }
}