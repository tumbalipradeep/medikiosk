package in.devmedi.kiosk.module.document.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.document.service.ClinicalDocumentService;
import in.devmedi.kiosk.module.document.service.ClinicalDocumentService.ClinicalDocumentMetadata;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/patient/cases/{caseId}/documents")
public class ClinicalDocumentController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalDocumentController.class);

    private final ClinicalDocumentService documentService;

    public ClinicalDocumentController(ClinicalDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<?> upload(@PathVariable String caseId,
                                    @RequestParam("file") MultipartFile file,
                                    @AuthenticationPrincipal ApplicationUserDetails principal) {
        try {
            ClinicalDocumentMetadata metadata = documentService.upload(caseId, principal.getId(), file);
            return ResponseEntity.ok(metadata);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException | RuntimeException e) {
            log.error("Clinical document upload failed for case {}", caseId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed"));
        }
    }

    @GetMapping
    public ResponseEntity<List<ClinicalDocumentMetadata>> list(@PathVariable String caseId,
                                                               @AuthenticationPrincipal ApplicationUserDetails principal) {
        return ResponseEntity.ok(documentService.listByUserCase(caseId, principal.getId()));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> delete(@PathVariable String caseId,
                                    @PathVariable String documentId,
                                    @AuthenticationPrincipal ApplicationUserDetails principal) {
        boolean deleted = documentService.delete(documentId, principal.getId());
        if (deleted) {
            return ResponseEntity.ok(Map.of("deleted", true));
        }
        return ResponseEntity.notFound().build();
    }
}