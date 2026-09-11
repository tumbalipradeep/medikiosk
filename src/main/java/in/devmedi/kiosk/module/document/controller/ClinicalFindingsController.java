package in.devmedi.kiosk.module.document.controller;

import in.devmedi.kiosk.module.document.findings.StructuredFindingsResponse;
import in.devmedi.kiosk.module.document.findings.service.ClinicalFindingsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Physician-facing structured-findings endpoint scoped to a reviewed case.
 *
 * <p>Like the extraction endpoint it lives under {@code /physician/**} (so it
 * requires {@code ROLE_PHYSICIAN}), verifies document/case ownership in the
 * service layer, and never exposes JPA entities or filesystem paths.</p>
 */
@RestController
@RequestMapping("/physician/cases/{caseId}/documents")
public class ClinicalFindingsController {

    private final ClinicalFindingsService findingsService;

    public ClinicalFindingsController(ClinicalFindingsService findingsService) {
        this.findingsService = findingsService;
    }

    @GetMapping("/{documentId}/findings")
    public StructuredFindingsResponse findings(@PathVariable String caseId, @PathVariable String documentId) {
        return findingsService.findingsForCaseAndDocument(caseId, documentId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }
}