package in.devmedi.kiosk.module.physician.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.document.findings.DocumentDetailResponse;
import in.devmedi.kiosk.module.document.findings.TimelineResponse;
import in.devmedi.kiosk.module.physician.assignment.CaseAccessDeniedException;
import in.devmedi.kiosk.module.physician.assignment.CaseAssignmentService;
import in.devmedi.kiosk.module.physician.workspace.CaseNotFoundException;
import in.devmedi.kiosk.module.physician.service.PhysicianTimelineService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Physician-only endpoints for the derived clinical timeline and the document
 * workspace detail view. Routes live under {@code /physician/**} so they
 * require {@code ROLE_PHYSICIAN}. Case access is enforced via the assignment
 * service so a physician can never silently open another physician's active
 * case. Only DTOs are returned - never JPA entities or filesystem paths.
 */
@RestController
@RequestMapping("/physician/cases/{caseId}")
public class PhysicianTimelineController {

    private final PhysicianTimelineService timelineService;
    private final CaseAssignmentService assignmentService;

    public PhysicianTimelineController(PhysicianTimelineService timelineService,
                                       CaseAssignmentService assignmentService) {
        this.timelineService = timelineService;
        this.assignmentService = assignmentService;
    }

    @GetMapping("/timeline")
    public TimelineResponse timeline(@PathVariable String caseId,
                                     @AuthenticationPrincipal ApplicationUserDetails actor) {
        assignmentService.requireAccess(caseId, actor == null ? null : actor.getId());
        timelineService.verifyCaseExists(caseId);
        return timelineService.timeline(caseId);
    }

    @GetMapping("/documents/{documentId}/detail")
    public DocumentDetailResponse detail(@PathVariable String caseId,
                                         @PathVariable String documentId,
                                         @AuthenticationPrincipal ApplicationUserDetails actor) {
        assignmentService.requireAccess(caseId, actor == null ? null : actor.getId());
        timelineService.verifyCaseExists(caseId);
        return timelineService.documentDetail(caseId, documentId);
    }

    @ExceptionHandler(CaseNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleCaseNotFound(CaseNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(CaseAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleAccessDenied(CaseAccessDeniedException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }
}