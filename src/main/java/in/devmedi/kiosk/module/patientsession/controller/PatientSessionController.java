package in.devmedi.kiosk.module.patientsession.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.clinical.controller.ClinicalIntakeConversationController;
import in.devmedi.kiosk.module.clinical.controller.PatientIntakeLanguageController;
import in.devmedi.kiosk.module.patientsession.service.PatientSessionService;
import in.devmedi.kiosk.module.voice.language.LanguageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/patient")
public class PatientSessionController {

    private final PatientSessionService patientSessionService;
    private final LanguageService languageService;

    public PatientSessionController(PatientSessionService patientSessionService,
                                    LanguageService languageService) {
        this.patientSessionService = patientSessionService;
        this.languageService = languageService;
    }

    @PostMapping("/session/start")
    public String startSession(@AuthenticationPrincipal ApplicationUserDetails user,
                               RedirectAttributes ra,
                               HttpSession httpSession) {
        if (user == null) {
            return "redirect:/login";
        }
        try {
            patientSessionService.start(user.getId());
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/patient/consent";
        }
        clearStagingHttpSession(httpSession);
        ra.addFlashAttribute("message", "Patient session started.");
        return "redirect:/patient/intake";
    }

    @GetMapping("/intake")
    public String intake(@AuthenticationPrincipal ApplicationUserDetails user, Model model, HttpSession session) {
        model.addAttribute("user", user);
        model.addAttribute("supportedLanguages", languageService.supportedLanguages());
        model.addAttribute("currentLanguage", languageService.resolve(
                (String) session.getAttribute(PatientIntakeLanguageController.LANGUAGE_ATTRIBUTE)));
        model.addAttribute("completedCaseId",
                session.getAttribute(ClinicalIntakeConversationController.COMPLETED_CASE_ATTRIBUTE));
        return "patient/intake";
    }

    /**
     * Removes the staging conversation state (answers, displayed wording, and the
     * completed-case marker) from the HTTP session whenever a genuinely new
     * patient session is started, so a returning patient never regresses to a
     * previous finished conversation in a stale browser session.
     */
    private void clearStagingHttpSession(HttpSession httpSession) {
        httpSession.removeAttribute(ClinicalIntakeConversationController.RESULT_ATTRIBUTE);
        httpSession.removeAttribute(ClinicalIntakeConversationController.DISPLAYED_ATTRIBUTE);
        httpSession.removeAttribute(ClinicalIntakeConversationController.COMPLETED_CASE_ATTRIBUTE);
    }
}
