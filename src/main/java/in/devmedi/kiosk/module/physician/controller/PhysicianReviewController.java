package in.devmedi.kiosk.module.physician.controller;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import in.devmedi.kiosk.module.clinical.summary.ClinicalSummary;
import in.devmedi.kiosk.module.clinical.summary.ClinicalSummaryBuilder;
import in.devmedi.kiosk.module.document.extraction.DocumentWithExtraction;
import in.devmedi.kiosk.module.document.extraction.ExtractionSummary;
import in.devmedi.kiosk.module.document.findings.DocumentWorkspaceItem;
import in.devmedi.kiosk.module.document.service.ClinicalDocumentExtractionService;
import in.devmedi.kiosk.module.document.service.ClinicalDocumentService;
import in.devmedi.kiosk.module.document.service.ClinicalDocumentService.ClinicalDocumentMetadata;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import in.devmedi.kiosk.module.physician.service.CompletedCaseReviewStore;
import in.devmedi.kiosk.module.physician.service.PhysicianTimelineService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class PhysicianReviewController {

    private final CompletedCaseReviewStore reviewStore;
    private final CompletedCasePersistenceService casePersistence;
    private final ClinicalSummaryBuilder summaryBuilder;
    private final ClinicalDocumentService documentService;
    private final ClinicalDocumentExtractionService extractionService;
    private final PhysicianTimelineService timelineService;

    public PhysicianReviewController(CompletedCaseReviewStore reviewStore,
                                     CompletedCasePersistenceService casePersistence,
                                     ClinicalSummaryBuilder summaryBuilder,
                                     ClinicalDocumentService documentService,
                                     ClinicalDocumentExtractionService extractionService,
                                     PhysicianTimelineService timelineService) {
        this.reviewStore = reviewStore;
        this.casePersistence = casePersistence;
        this.summaryBuilder = summaryBuilder;
        this.documentService = documentService;
        this.extractionService = extractionService;
        this.timelineService = timelineService;
    }

    @GetMapping("/physician/review")
    public String review(@AuthenticationPrincipal ApplicationUserDetails user, Model model) {
        Optional<CompletedCase> latest = reviewStore.latest().or(() -> casePersistence.findLatest());
        model.addAttribute("user", user);
        model.addAttribute("role", "Physician");
        if (latest.isPresent()) {
            CompletedCase completedCase = latest.get();
            ClinicalSummary summary = summaryBuilder.summarize(completedCase.result());
            model.addAttribute("hasCompletedCase", true);
            model.addAttribute("caseId", completedCase.id());
            model.addAttribute("sections", summary.sections());
            model.addAttribute("answeredCount", summary.answeredCount());
            model.addAttribute("documents", attachExtractionStates(completedCase.id()));
            model.addAttribute("workspace", timelineService.workspaceItems(completedCase.id()));
        } else {
            model.addAttribute("hasCompletedCase", false);
            model.addAttribute("sections", List.of());
            model.addAttribute("answeredCount", 0);
            model.addAttribute("documents", List.of());
            model.addAttribute("workspace", List.of());
        }
        return "physician/review";
    }

    private List<DocumentWithExtraction> attachExtractionStates(String caseId) {
        Map<String, ExtractionSummary> byDocumentId = extractionService.summariesForCase(caseId).stream()
                .collect(Collectors.toMap(ExtractionSummary::documentId, Function.identity()));
        return documentService.listByCase(caseId).stream()
                .map(document -> new DocumentWithExtraction(document,
                        byDocumentId.getOrDefault(document.documentId(),
                                ExtractionSummary.pending(document.documentId()))))
                .toList();
    }
}