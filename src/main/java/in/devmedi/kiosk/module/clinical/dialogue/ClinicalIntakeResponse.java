package in.devmedi.kiosk.module.clinical.dialogue;

import in.devmedi.kiosk.module.clinical.redflag.RedFlag;
import in.devmedi.kiosk.module.clinical.redflag.RedFlagSeverity;

import java.util.List;

/**
 * Backend response for one step of the patient intake conversation.
 *
 * <p>Carries the deterministic next question together with any detected red
 * flags and the overall urgency of the current step. The question is described
 * by a render-ready {@link IntakeQuestion} so the UI can identify the current
 * section (HPI/SOCRATES or Dashavidha) regardless of which planner produced it.</p>
 *
 * @param question  the single next question, or {@code null} when the dialogue is complete
 * @param state     overall dialogue state after this step
 * @param completed whether the clinical intake sequence is finished
 * @param redFlags  zero or more detected red flags (deterministic)
 * @param urgency   overall urgency across all detected red flags
 * @param caseId    the completed case identity, present only when {@code completed} is true
 */
public record ClinicalIntakeResponse(IntakeQuestion question,
                                     DialogueState state,
                                     boolean completed,
                                     List<RedFlag> redFlags,
                                     RedFlagSeverity urgency,
                                     String caseId) {
}