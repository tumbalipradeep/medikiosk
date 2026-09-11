package in.devmedi.kiosk.module.document.controller;

import in.devmedi.kiosk.module.document.extraction.ExtractionResult;
import in.devmedi.kiosk.module.document.service.ClinicalDocumentExtractionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Physician-facing text-extraction endpoints scoped to a reviewed case.
 *
 * <p>Route is under {@code /physician/**} so it requires {@code ROLE_PHYSICIAN};
 * patients cannot invoke it. Case/document ownership is verified in the service
 * layer and only DTOs are returned - never JPA entities or filesystem paths.</p>
 */
@RestController
@RequestMapping("/physician/cases/{caseId}/documents")
public class ClinicalDocumentExtractionController {

    private final ClinicalDocumentExtractionService extractionService;

    public ClinicalDocumentExtractionController(ClinicalDocumentExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @GetMapping("/{documentId}/extraction")
    public ExtractionResult current(@PathVariable String caseId, @PathVariable String documentId) {
        return extractionService.current(caseId, documentId)
                .orElseThrow(() -> new IllegalArgumentException("Extraction not found for the given document"));
    }

    @PostMapping("/{documentId}/extraction")
    public ExtractionResult extract(@PathVariable String caseId, @PathVariable String documentId) {
        return extractionService.extract(caseId, documentId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }
}