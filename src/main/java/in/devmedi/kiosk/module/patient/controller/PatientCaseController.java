package in.devmedi.kiosk.module.patient.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.clinical.redflag.RedFlag;
import in.devmedi.kiosk.module.clinical.redflag.RedFlagEvaluator;
import in.devmedi.kiosk.module.clinical.redflag.RedFlagSeverity;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Patient-safe read access to a completed case for the review step of the kiosk
 * journey.
 *
 * <p>Returns only what a patient is entitled to see about their own persisted
 * case: the verbatim answers (with the wording that was actually shown), per
 * answer and overall, plus a deterministic re-evaluation of the same offline
 * red-flag rules so the review screen can honestly say whether anything was
 * flagged for clinical review. The owning patient is enforced — a patient can
 * never read another patient's case through this endpoint. Nothing here is a
 * diagnosis or treatment instruction.</p>
 */
@RestController
@RequestMapping("/patient/cases")
public class PatientCaseController {

    private final CompletedCasePersistenceService casePersistence;
    private final RedFlagEvaluator redFlagEvaluator;

    public PatientCaseController(CompletedCasePersistenceService casePersistence,
                                 RedFlagEvaluator redFlagEvaluator) {
        this.casePersistence = casePersistence;
        this.redFlagEvaluator = redFlagEvaluator;
    }

    @GetMapping("/{caseId}/summary")
    public PatientCaseSummary summary(@PathVariable("caseId") String caseId,
                                      @AuthenticationPrincipal ApplicationUserDetails principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        CompletedCase completed = casePersistence.findByCaseId(caseId)
                .filter(c -> principal.getId().equals(c.userId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Case not found for this patient"));
        return toSummary(completed);
    }

    private PatientCaseSummary toSummary(CompletedCase completed) {
        ClinicalConversationResult result = completed.result();
        int flaggedCount = 0;
        List<SummaryItem> items = new java.util.ArrayList<>();
        for (ClinicalAnswer answer : result.all()) {
            List<RedFlag> flags = redFlagEvaluator.evaluate(answer.answer());
            if (!flags.isEmpty()) {
                flaggedCount += flags.size();
            }
            items.add(new SummaryItem(
                    answer.questionId(),
                    answer.section(),
                    answer.displayedQuestionText(),
                    answer.answer(),
                    answer.questionSource().name(),
                    answer.answerSource().name(),
                    answer.language()));
        }
        RedFlagSeverity severity = flaggedCount > 0 ? RedFlagSeverity.URGENT : RedFlagSeverity.NONE;
        return new PatientCaseSummary(completed.id(), items.size(), flaggedCount, severity.name(), List.copyOf(items));
    }

    /** Patient-safe review payload. */
    public record PatientCaseSummary(String caseId,
                                     int answeredCount,
                                     int flaggedCount,
                                     String flaggedSeverity,
                                     List<SummaryItem> answers) {
    }

    /** One answer shown in the patient review. */
    public record SummaryItem(String questionId,
                              String section,
                              String displayedText,
                              String answer,
                              String questionSource,
                              String answerSource,
                              String language) {
    }
}