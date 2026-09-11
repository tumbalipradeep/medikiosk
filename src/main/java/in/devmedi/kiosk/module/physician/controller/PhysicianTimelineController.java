package in.devmedi.kiosk.module.physician.controller;

import in.devmedi.kiosk.module.document.findings.DocumentDetailResponse;
import in.devmedi.kiosk.module.document.findings.TimelineResponse;
import in.devmedi.kiosk.module.physician.service.PhysicianTimelineService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Physician-only endpoints for the derived clinical timeline and the document
 * workspace detail view.
 *
 * <p>Routes live under {@code /physician/**} so they require
 * {@code ROLE_PHYSICIAN}. Every request verifies that the case belongs to the
 * calling physician; unrelated cases and documents resolve to 404. Only DTOs
 * are returned - never JPA entities or filesystem paths.</p>
 */
@RestController
@RequestMapping("/physician/cases/{caseId}")
public class PhysicianTimelineController {

    private final PhysicianTimelineService timelineService;

    public PhysicianTimelineController(PhysicianTimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping("/timeline")
    public TimelineResponse timeline(@PathVariable String caseId) {
        timelineService.verifyCaseExists(caseId);
        return timelineService.timeline(caseId);
    }

    @GetMapping("/documents/{documentId}/detail")
    public DocumentDetailResponse detail(@PathVariable String caseId,
                                         @PathVariable String documentId) {
        timelineService.verifyCaseExists(caseId);
        return timelineService.documentDetail(caseId, documentId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }
}