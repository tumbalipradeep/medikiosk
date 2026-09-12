package in.devmedi.kiosk.module.patient.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.clinical.controller.ClinicalIntakeConversationController;
import in.devmedi.kiosk.module.clinical.redflag.RedFlagEvaluator;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Honest completion screen for a finished kiosk visit.
 *
 * <p>Verifies the case actually belongs to the logged-in patient before
 * rendering, and states plainly that the visit was recorded locally in demo
 * mode with no ABHA/ABDM transmission. The case id is read from the HTTP
 * session rather than the URL, so no case reference ever appears in a query
 * string or leaks through the {@code Referer} header.</p>
 */
@Controller
@RequestMapping("/patient/intake")
public class PatientIntakeCompleteController {

    private final CompletedCasePersistenceService casePersistence;
    private final RedFlagEvaluator redFlagEvaluator;

    public PatientIntakeCompleteController(CompletedCasePersistenceService casePersistence,
                                           RedFlagEvaluator redFlagEvaluator) {
        this.casePersistence = casePersistence;
        this.redFlagEvaluator = redFlagEvaluator;
    }

    @GetMapping("/complete")
    public String complete(HttpSession session,
                           @AuthenticationPrincipal ApplicationUserDetails user,
                           Model model) {
        if (user == null) {
            return "redirect:/login";
        }
        String caseId = (String) session.getAttribute(
                ClinicalIntakeConversationController.COMPLETED_CASE_ATTRIBUTE);
        if (caseId == null) {
            return "redirect:/patient/home";
        }
        CompletedCase completed = casePersistence.findByCaseId(caseId)
                .filter(c -> user.getId().equals(c.userId()))
                .orElse(null);
        if (completed == null) {
            return "redirect:/patient/home";
        }
        long flagged = completed.result().all().stream()
                .flatMap(answer -> redFlagEvaluator.evaluate(answer.answer()).stream())
                .count();
        model.addAttribute("user", user);
        model.addAttribute("caseId", completed.id());
        model.addAttribute("answeredCount", completed.result().size());
        model.addAttribute("flaggedCount", flagged);
        return "patient/complete";
    }
}