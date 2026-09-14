package in.devmedi.kiosk.module.clinical.adaptive;

/**
 * View model returned to the adaptive-conversation UI.
 *
 * @param started        whether a conversation is in progress
 * @param completed      whether all applicable questions have been asked
 * @param questionId     current question id (null when completed)
 * @param questionText   current question text (null when completed)
 * @param categoryLabel  readable history category of the current question
 * @param answeredCount  number of questions answered so far
 * @param remainingCount number of currently-applicable questions still pending
 * @param aiGuided       whether the current question was chosen by the AI
 *                       advisor rather than the deterministic planner
 */
public record AdaptiveView(
        boolean started,
        boolean completed,
        String questionId,
        String questionText,
        String categoryLabel,
        int answeredCount,
        int remainingCount,
        boolean aiGuided) {

    public static AdaptiveView none() {
        return new AdaptiveView(false, true, null, null, null, 0, 0, false);
    }
}