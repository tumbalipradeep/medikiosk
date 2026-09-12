package in.devmedi.kiosk.module.physician.review;

/**
 * Request for a physician review decision against one captured answer.
 *
 * <p>{@code decision} is one of {@code ACCEPTED}, {@code AMENDED},
 * {@code REJECTED}. {@code amendedText} is required (and only meaningful) for
 * {@code AMENDED}; it is ignored-and-cleared otherwise. {@code rationale} is an
 * optional free-text note and is never displayed to the patient.</p>
 */
public record ReviewRequest(String decision, String amendedText, String rationale) {
}