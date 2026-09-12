package in.devmedi.kiosk.module.physician.review;

/**
 * The clinical decision a physician records against one captured answer.
 *
 * <p>Decisions are applied to the stable, source-evidence answer held in
 * {@code completed_case_answers}. Nothing here replaces the original patient
 * answer — {@code AMENDED} stores an alternative reviewed text next to it,
 * {@code REJECTED} records that the evidence could not be used, and
 * {@code ACCEPTED} confirms the evidence as captured.</p>
 */
public enum ReviewDecision {

    /** The captured answer is accepted as accurate and usable. */
    ACCEPTED,

    /** The physician provides a corrected/amended text for the answer. */
    AMENDED,

    /** The captured answer is rejected as unreliable and excluded from use. */
    REJECTED
}