package in.devmedi.kiosk.module.document.findings.service;

import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import in.devmedi.kiosk.module.document.findings.StructuredFindingsResponse;
import in.devmedi.kiosk.module.document.findings.analysis.FindingsAnalysisService;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindings;
import in.devmedi.kiosk.module.document.findings.model.StructuredFindings;
import in.devmedi.kiosk.module.document.findings.parser.ClinicalFindingsParser;
import in.devmedi.kiosk.module.document.findings.repository.ClinicalDocumentFindingsRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentExtractionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates, persists and serves structured findings for a document's
 * extraction.
 *
 * <p>Findings are always derived deterministically from the already-extracted
 * document text and are scoped to the reviewed case like every other document
 * endpoint: a {code caseId}/{code documentId} pair that does not belong
 * together is rejected. Generation is idempotent - re-running never changes an
 * existing findings row.</p>
 */
@Service
public class ClinicalFindingsService {

    private final ClinicalDocumentExtractionRepository extractionRepository;
    private final ClinicalDocumentFindingsRepository findingsRepository;
    private final ClinicalFindingsParser parser;
    private final StructuredFindingsMapper mapper;
    private final FindingsAnalysisService analysisService;

    public ClinicalFindingsService(ClinicalDocumentExtractionRepository extractionRepository,
                                   ClinicalDocumentFindingsRepository findingsRepository,
                                   ClinicalFindingsParser parser,
                                   StructuredFindingsMapper mapper,
                                   FindingsAnalysisService analysisService) {
        this.extractionRepository = extractionRepository;
        this.findingsRepository = findingsRepository;
        this.parser = parser;
        this.mapper = mapper;
        this.analysisService = analysisService;
    }

    @Transactional
    public StructuredFindingsResponse findingsForCaseAndDocument(String caseId, String documentId) {
        ClinicalDocumentExtraction extraction = extractionRepository
                .findByDocument_DocumentIdAndDocument_CompletedCase_CaseId(documentId, caseId)
                .orElseThrow(() -> new IllegalArgumentException("Extraction not found for the given document"));

        ClinicalDocumentFindings findings = ensureFindings(extraction);
        return mapper.toResponse(documentId, extraction, findings);
    }

    /**
     * Idempotent hook used by the extraction service: computes and persists
     * findings right after text is extracted. No-op for non-text outcomes or
     * when findings already exist.
     */
    @Transactional
    public void generateForExtraction(ClinicalDocumentExtraction extraction) {
        if (!hasExtractableText(extraction)) {
            return;
        }
        ensureFindings(extraction);
    }

    private ClinicalDocumentFindings ensureFindings(ClinicalDocumentExtraction extraction) {
        if (!hasExtractableText(extraction)) {
            return null;
        }
        return findingsRepository.findByExtraction(extraction)
                .orElseGet(() -> findingsRepository.save(
                        ClinicalDocumentFindings.create(extraction,
                                analysisService.analyze(parser.parse(extraction.getExtractedText())))));
    }

    private boolean hasExtractableText(ClinicalDocumentExtraction extraction) {
        return extraction.getStatus() == ExtractionStatus.EXTRACTED
                && extraction.getExtractedText() != null
                && !extraction.getExtractedText().isBlank();
    }
}