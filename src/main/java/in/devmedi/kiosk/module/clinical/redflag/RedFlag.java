package in.devmedi.kiosk.module.clinical.redflag;

/**
 * A detected red flag returned to the intake conversation.
 *
 * @param id       stable red-flag id (e.g. {@code red_flag_breathing})
 * @param severity severity of the warning
 * @param title    short human-readable title
 * @param message  clinical warning message (informational only, never a diagnosis)
 */
public record RedFlag(String id, RedFlagSeverity severity, String title, String message) {
}