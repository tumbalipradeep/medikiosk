package in.devmedi.kiosk.module.physician.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.clinical.triage.FlagAssessmentAction;
import in.devmedi.kiosk.module.clinical.triage.RedFlagAssessment;
import in.devmedi.kiosk.module.clinical.triage.RedFlagAssessmentService;
import in.devmedi.kiosk.module.clinical.triage.TriageSeverity;
import in.devmedi.kiosk.module.physician.assignment.CaseAccessDeniedException;
import in.devmedi.kiosk.module.physician.assignment.CaseAssignmentService;
import in.devmedi.kiosk.module.physician.clinicalrecord.ClinicalSummary;
import in.devmedi.kiosk.module.physician.clinicalrecord.ClinicalSummaryService;
import in.devmedi.kiosk.module.physician.clinicalrecord.Consultation;
import in.devmedi.kiosk.module.physician.clinicalrecord.ConsultationService;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import in.devmedi.kiosk.module.physician.workspace.CaseNotFoundException;
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
 * Physician-facing clinical record for a case: red-flag triage assessments,
 * the editable clinical summary sections, and the final consultation.
 *
 * <p>Everything here is the human clinical overlay. Flags are documented
 * actions; summary sections must be reviewed and explicitly accepted before
 * they can count as the physician's record; the consultation is always
 * physician-authored. AI/system content is never silently upgraded.</p>
 */
@Controller
public class PhysicianClinicalRecordController {

    private final RedFlagAssessmentService triageService;
    private final ClinicalSummaryService summaryService;
    private final ConsultationService consultationService;
    private final CompletedCasePersistenceService casePersistence;
    private final CaseAssignmentService assignmentService;

    public PhysicianClinicalRecordController(RedFlagAssessmentService triageService,
                                             ClinicalSummaryService summaryService,
                                             ConsultationService consultationService,
                                             CompletedCasePersistenceService casePersistence,
                                             CaseAssignmentService assignmentService) {
        this.triageService = triageService;
        this.summaryService = summaryService;
        this.consultationService = consultationService;
        this.casePersistence = casePersistence;
        this.assignmentService = assignmentService;
    }

    @GetMapping("/physician/cases/{caseId}/record")
    public String record(@PathVariable String caseId,
                         @AuthenticationPrincipal ApplicationUserDetails user,
                         Model model) {
        assignmentService.requireAccess(caseId, user == null ? null : user.getId());
        casePersistence.findByCaseId(caseId)
                .orElseThrow(() -> new RecordNotFoundException(caseId));
        List<RedFlagAssessment> assessments = triageService.assessmentsForCase(caseId);
        List<ClinicalSummary> summaries = summaryService.sectionsForCase(caseId);
        Consultation consultation = consultationService.forCase(caseId).orElse(null);
        model.addAttribute("user", user);
        model.addAttribute("role", "Physician");
        model.addAttribute("caseId", caseId);
        model.addAttribute("assessments", assessments);
        model.addAttribute("hasAssessments", !assessments.isEmpty());
        model.addAttribute("flaggedCount", assessments.stream()
                .filter(a -> a.getSeverity() != TriageSeverity.NONE).count());
        model.addAttribute("overallSeverity", triageService.overallSeverity(caseId).name());
        model.addAttribute("summaries", summaries);
        model.addAttribute("hasSummaries", !summaries.isEmpty());
        model.addAttribute("consultation", consultation);
        model.addAttribute("hasConsultation", consultation != null);
        return "physician/record";
    }

    @PostMapping("/physician/cases/{caseId}/flags/{flagId}/action")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> takeFlagAction(
            @PathVariable String caseId,
            @PathVariable String flagId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal ApplicationUserDetails actor) {
        String action = body.get("action");
        String note = body.getOrDefault("note", "");
        Long physicianId = actor == null ? null : actor.getId();
        require(caseId, actor);
        if (action == null || action.isBlank() || caseId == null || caseId.isBlank() || flagId == null || flagId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        triageService.physicianAction(caseId, flagId, action, note, physicianId);
        return ResponseEntity.ok(Map.of(
                "flagId", flagId,
                "action", FlagAssessmentAction.normalize(action) == null ? "" : FlagAssessmentAction.normalize(action).name(),
                "note", note == null ? "" : note));
    }

    @PostMapping("/physician/cases/{caseId}/summary/{section}/amend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> amendSummary(
            @PathVariable String caseId,
            @PathVariable String section,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal ApplicationUserDetails actor) {
        require(caseId, actor);
        summaryService.amend(caseId, section, body.getOrDefault("content", ""),
                actor == null ? null : actor.getId());
        return ResponseEntity.ok(Map.of("section", section, "status", "DRAFT"));
    }

    @PostMapping("/physician/cases/{caseId}/summary/{section}/accept")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> acceptSummary(
            @PathVariable String caseId,
            @PathVariable String section,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal ApplicationUserDetails actor) {
        require(caseId, actor);
        summaryService.accept(caseId, section, body.getOrDefault("content", null),
                actor == null ? null : actor.getId());
        return ResponseEntity.ok(Map.of("section", section, "status", "ACCEPTED"));
    }

    @PostMapping("/physician/cases/{caseId}/summary/{section}/reject")
    @ResponseBody
public ResponseEntity<Map<String, Object>> rejectSummary(
            @PathVariable String caseId,
            @PathVariable String section,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal ApplicationUserDetails actor) {
        require(caseId, actor);
        summaryService.reject(caseId, section, actor == null ? null : actor.getId());
        return ResponseEntity.ok(Map.of("section", section, "status", "REJECTED"));
    }

    @PostMapping("/physician/cases/{caseId}/consultation")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveConsultation(
            @PathVariable String caseId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal ApplicationUserDetails actor) {
        if (actor == null) {
            return ResponseEntity.status(401).build();
        }
        require(caseId, actor);
        Consultation saved = consultationService.createOrUpdate(
                caseId,
                actor.getId(),
                body.getOrDefault("assessment", ""),
                body.getOrDefault("plan", ""),
                body.getOrDefault("advice", ""),
                body.getOrDefault("followUp", ""));
        return ResponseEntity.ok(Map.of("status", saved.getStatus().name()));
    }

    @PostMapping("/physician/cases/{caseId}/consultation/finalize")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> finalizeConsultation(
            @PathVariable String caseId,
            @AuthenticationPrincipal ApplicationUserDetails actor) {
        if (actor == null) {
            return ResponseEntity.status(401).build();
        }
        require(caseId, actor);
        consultationService.finalizeRecord(caseId, actor.getId());
        assignmentService.complete(caseId, actor.getId());
        return ResponseEntity.ok(Map.of("status", "FINALIZED"));
    }

    public static class RecordNotFoundException extends RuntimeException {
        public RecordNotFoundException(String caseId) {
            super("Case not found: " + caseId);
        }
    }

    private void require(String caseId, ApplicationUserDetails actor) {
        assignmentService.requireAccess(caseId, actor == null ? null : actor.getId());
    }

    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(RecordNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CaseNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCaseNotFound(CaseNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CaseAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(CaseAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Bad request", "message", ex.getMessage() == null ? "Invalid input" : ex.getMessage()));
    }
}