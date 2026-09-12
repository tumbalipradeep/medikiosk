package in.devmedi.kiosk.module.physician.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.physician.review.ReviewRequest;
import in.devmedi.kiosk.module.physician.workspace.CaseListItem;
import in.devmedi.kiosk.module.physician.workspace.CaseNotFoundException;
import in.devmedi.kiosk.module.physician.workspace.PhysicianCaseWorkspaceService;
import in.devmedi.kiosk.module.physician.workspace.PhysicianReviewService;
import in.devmedi.kiosk.module.physician.workspace.ReviewValidationException;
import in.devmedi.kiosk.module.physician.workspace.WorkspaceView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

/**
 * Physician dashboard and per-case workspace, plus the accept/amend/reject
 * review API. Routes live under {@code /physician/**} so {@code ROLE_PHYSICIAN}
 * is required by the existing {@code SecurityConfig} path rule.
 */
@Controller
public class PhysicianCaseWorkspaceController {

    private final PhysicianCaseWorkspaceService workspaceService;
    private final PhysicianReviewService reviewService;

    public PhysicianCaseWorkspaceController(PhysicianCaseWorkspaceService workspaceService,
                                            PhysicianReviewService reviewService) {
        this.workspaceService = workspaceService;
        this.reviewService = reviewService;
    }

    @GetMapping("/physician/home")
    public String home(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        List<CaseListItem> cases = workspaceService.dashboardCases();
        model.addAttribute("user", user);
        model.addAttribute("role", "Physician");
        model.addAttribute("cases", cases);
        model.addAttribute("hasCases", !cases.isEmpty());
        return "physician/dashboard";
    }

    @GetMapping("/physician/cases/{caseId}")
    public String caseWorkspace(@PathVariable String caseId,
                                @AuthenticationPrincipal ApplicationUserDetails user,
                                Model model) {
        WorkspaceView view = workspaceService.workspace(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
        model.addAttribute("user", user);
        model.addAttribute("role", "Physician");
        model.addAttribute("workspace", view);
        return "physician/case";
    }

    @GetMapping("/physician/cases/{caseId}/reviews")
    @ResponseBody
    public List<Map<String, Object>> reviews(@PathVariable String caseId) {
        return reviewService.reviews(caseId).stream()
                .map(r -> Map.<String, Object>of(
                        "answerOrder", r.getAnswerOrder(),
                        "decision", r.getDecision().name(),
                        "amendedText", r.getAmendedText() == null ? "" : r.getAmendedText(),
                        "rationale", r.getRationale() == null ? "" : r.getRationale(),
                        "reviewer", r.getReviewerUsername(),
                        "decidedAt", r.getDecidedAt() == null ? "" : r.getDecidedAt().toString()))
                .toList();
    }

    @PostMapping("/physician/cases/{caseId}/answers/{answerOrder}/review")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitReview(
            @PathVariable String caseId,
            @PathVariable int answerOrder,
            @RequestBody ReviewRequest request,
            @AuthenticationPrincipal ApplicationUserDetails actor) {
        String reviewer = actor == null ? "unknown" : actor.getUsername();
        var entry = reviewService.saveReview(caseId, answerOrder, request, reviewer);
        return ResponseEntity.ok(Map.of(
                "answerOrder", entry.getAnswerOrder(),
                "decision", entry.getDecision().name(),
                "amendedText", entry.getAmendedText() == null ? "" : entry.getAmendedText(),
                "rationale", entry.getRationale() == null ? "" : entry.getRationale(),
                "reviewer", entry.getReviewerUsername(),
                "decidedAt", entry.getDecidedAt().toString()));
    }

    @ExceptionHandler(CaseNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(CaseNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ReviewValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(ReviewValidationException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}