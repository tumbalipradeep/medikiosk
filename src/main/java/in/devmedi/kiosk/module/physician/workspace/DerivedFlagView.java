package in.devmedi.kiosk.module.physician.workspace;

/**
 * A red flag re-derived deterministically from one persisted patient answer.
 *
 * <p>Wording is deliberately informational ("system-generated concern") and
 * never a diagnostic claim. It is surfaced for physician review only.</p>
 */
public record DerivedFlagView(String id, String severity, String title, String message) {
}