package in.devmedi.kiosk.module.physician.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.clinical.summary.ClinicalSummary;
import in.devmedi.kiosk.module.clinical.summary.ClinicalSummaryBuilder;
import in.devmedi.kiosk.module.physician.service.CompletedCaseReviewStore;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Optional;

@Controller
public class PhysicianReviewController {

    private final CompletedCaseReviewStore reviewStore;
    private final ClinicalSummaryBuilder summaryBuilder;

    public PhysicianReviewController(CompletedCaseReviewStore reviewStore,
                                     ClinicalSummaryBuilder summaryBuilder) {
        this.reviewStore = reviewStore;
        this.summaryBuilder = summaryBuilder;
    }

    @GetMapping("/physician/review")
    public String review(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        Optional<ClinicalConversationResult> latest = reviewStore.latest();
        model.addAttribute("user", user);
        model.addAttribute("role", "Physician");
        if (latest.isPresent()) {
            ClinicalSummary summary = summaryBuilder.summarize(latest.get());
            model.addAttribute("hasCompletedCase", true);
            model.addAttribute("sections", summary.sections());
            model.addAttribute("answeredCount", summary.answeredCount());
        } else {
            model.addAttribute("hasCompletedCase", false);
            model.addAttribute("sections", List.of());
            model.addAttribute("answeredCount", 0);
        }
        return "physician/review";
    }
}