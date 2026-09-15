package in.devmedi.kiosk.module.document.service;

import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.extraction.DocumentTextProcessor;
import in.devmedi.kiosk.module.document.extraction.ExtractionErrorCategory;
import in.devmedi.kiosk.module.document.extraction.ExtractionMethod;
import in.devmedi.kiosk.module.document.extraction.ExtractionOutcome;
import in.devmedi.kiosk.module.document.extraction.ExtractionResult;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import in.devmedi.kiosk.module.document.extraction.ExtractionSummary;
import in.devmedi.kiosk.module.document.findings.service.ClinicalFindingsService;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentExtractionRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentRepository;
import in.devmedi.kiosk.module.document.storage.SecureFileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Orchestrates text extraction for clinical documents and persists the results.
 *
 * <p>Extraction is scoped to a case: every operation first confirms the document
 * belongs to the given case so a physician can never trigger or read extraction
 * for a document outside the case they are reviewing. Existing results are
 * returned as-is and the original uploaded binary is never modified.</p>
 */
@Service
public class ClinicalDocumentExtractionService {

    private final ClinicalDocumentRepository documentRepository;
    private final ClinicalDocumentExtractionRepository extractionRepository;
    private final SecureFileStorage fileStorage;
    private final DocumentTextProcessor textProcessor;
    private final ClinicalFindingsService findingsService;

    public ClinicalDocumentExtractionService(ClinicalDocumentRepository documentRepository,
                                             ClinicalDocumentExtractionRepository extractionRepository,
                                             SecureFileStorage fileStorage,
                                             DocumentTextProcessor textProcessor,
                                             ClinicalFindingsService findingsService) {
        this.documentRepository = documentRepository;
        this.extractionRepository = extractionRepository;
        this.fileStorage = fileStorage;
        this.textProcessor = textProcessor;
        this.findingsService = findingsService;
    }

    @Transactional(readOnly = true)
    public List<ExtractionSummary> summariesForCase(String caseId) {
        List<ClinicalDocument> documents = documentRepository.findByCompletedCase_CaseIdOrderByUploadedAtAsc(caseId);
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<String, ExtractionSummary> byDocumentId = extractionRepository.findByDocumentIn(documents).stream()
                .map(ExtractionSummary::from)
                .collect(Collectors.toMap(ExtractionSummary::documentId, summary -> summary));
        return documents.stream()
                .map(document -> byDocumentId.getOrDefault(document.getDocumentId(),
                        ExtractionSummary.pending(document.getDocumentId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ExtractionResult> current(String caseId, String documentId) {
        return extractionRepository.findByDocument_DocumentIdAndDocument_CompletedCase_CaseId(documentId, caseId)
                .map(extraction -> ExtractionResult.from(extraction, documentId));
    }

    @Transactional
    public ExtractionResult extract(String caseId, String documentId) {
        ClinicalDocument document = findDocumentInCase(caseId, documentId);

        ClinicalDocumentExtraction extraction = extractionRepository.findByDocument(document).orElseGet(() -> {
            ExtractionOutcome outcome;
            try {
                outcome = textProcessor.process(
                        fileStorage.resolve(document.getStoredFilename()),
                        document.getContentType());
            } catch (IOException ex) {
                outcome = ExtractionOutcome.failed(ExtractionErrorCategory.UNKNOWN,
                        "Could not read the stored document: " + ex.getMessage());
            }
            ClinicalDocumentExtraction created = ClinicalDocumentExtraction.create(document, outcome);
            recordProvenance(created, document.getContentType(), outcome);
            return extractionRepository.saveAndFlush(created);
        });

        findingsService.generateForExtraction(extraction);
        return ExtractionResult.from(extraction, documentId);
    }

    private ClinicalDocument findDocumentInCase(String caseId, String documentId) {
        return documentRepository.findByDocumentId(documentId)
                .filter(document -> caseId.equals(document.getCompletedCase().getCaseId()))
                .orElseThrow(() -> new IllegalArgumentException("Document not found in the given case"));
    }

    private void recordProvenance(ClinicalDocumentExtraction extraction,
                                  String contentType,
                                  ExtractionOutcome outcome) {
        ExtractionMethod method = DocumentTextProcessor.methodFor(contentType);
        String provider = switch (method) {
            case PDF_TEXT -> "pdfbox";
            case OCR_IMAGE -> textProcessor.imageOcrAvailable()
                    ? textProcessor.imageOcrEngineName()
                    : textProcessor.imageOcrEngineName() + " (not available)";
            case NONE -> null;
        };
        extraction.recordProvenance(method, provider, null);
    }
}