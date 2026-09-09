package in.devmedi.kiosk.module.clinical.redflag;

/**
 * Severity level of a detected red flag.
 *
 * <p>The demo engine only distinguishes {@link #NONE} from {@link #URGENT}.
 * This is not a complete medical triage system.</p>
 */
public enum RedFlagSeverity {

    NONE,
    URGENT
}