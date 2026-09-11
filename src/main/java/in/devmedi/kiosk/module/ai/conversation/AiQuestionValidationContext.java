package in.devmedi.kiosk.module.ai.conversation;

import java.util.List;
import java.util.Set;

/**
 * Provider-neutral validation context. Carries only the clinical section name,
 * the deterministic follow-up topics the current step permits, the objectives
 * already covered, the canonical texts already asked, the canonical objective
 * being collected, and the patient's latest answer — so the validator depends
 * on plain strings and never on the clinical domain model.
 *
 * @param allowedFollowUpTopics set of topic tokens the current deterministic step permits
 * @param askedTopics           set of objective topics already covered (duplicates are repeats)
 * @param alreadyAskedQuestions canonical texts of every question already asked (duplicate guard)
 * @param targetQuestionText    canonical text of the objective being collected (boundary anchor)
 * @param latestAnswer          the patient's latest verbatim answer (clarification anchor)
 * @param sectionName           current deterministic clinical section
 */
public record AiQuestionValidationContext(Set<String> allowedFollowUpTopics,
                                          Set<String> askedTopics,
                                          List<String> alreadyAskedQuestions,
                                          String targetQuestionText,
                                          String latestAnswer,
                                          String sectionName) {

    public static AiQuestionValidationContext of(Set<String> allowedFollowUpTopics, String sectionName) {
        return new AiQuestionValidationContext(
                allowedFollowUpTopics == null ? Set.of() : Set.copyOf(allowedFollowUpTopics),
                Set.of(), List.of(), null, null, sectionName);
    }

    public static AiQuestionValidationContext of(Set<String> allowedFollowUpTopics,
                                                 Set<String> askedTopics,
                                                 List<String> alreadyAskedQuestions,
                                                 String targetQuestionText,
                                                 String latestAnswer,
                                                 String sectionName) {
        return new AiQuestionValidationContext(
                allowedFollowUpTopics == null ? Set.of() : Set.copyOf(allowedFollowUpTopics),
                askedTopics == null ? Set.of() : Set.copyOf(askedTopics),
                alreadyAskedQuestions == null ? List.of() : List.copyOf(alreadyAskedQuestions),
                targetQuestionText, latestAnswer, sectionName);
    }
}