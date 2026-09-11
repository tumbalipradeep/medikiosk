package in.devmedi.kiosk.module.document.entity;

import in.devmedi.kiosk.module.document.extraction.ExtractionErrorCategory;
import in.devmedi.kiosk.module.document.extraction.ExtractionOutcome;
import in.devmedi.kiosk.module.document.extraction.ExtractionResult;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted text-extraction result for one clinical document (1:1).
 *
 * <p>The original uploaded binary is never stored here; only the extracted
 * text and metadata describing the extraction run. Page-level text lives in
 * {@link ClinicalDocumentPageText} so ordering survives persistence.</p>
 */
@Entity
@Table(name = "clinical_document_extractions")
public class ClinicalDocumentExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinical_document_id", nullable = false)
    private ClinicalDocument document;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExtractionStatus status;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_category", length = 30)
    private ExtractionErrorCategory errorCategory;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "extracted_at", nullable = false, updatable = false)
    private Instant extractedAt;

    @OneToMany(mappedBy = "extraction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("pageNumber ASC")
    private List<ClinicalDocumentPageText> pages = new ArrayList<>();

    protected ClinicalDocumentExtraction() {
    }

    public static ClinicalDocumentExtraction create(ClinicalDocument document, ExtractionOutcome outcome) {
        ClinicalDocumentExtraction extraction = new ClinicalDocumentExtraction();
        extraction.document = document;
        extraction.status = outcome.status();
        extraction.extractedText = outcome.extractedText();
        extraction.pageCount = outcome.pageCount();
        extraction.errorCategory = outcome.errorCategory();
        extraction.errorMessage = outcome.errorMessage();
        for (ExtractionResult.PageText page : outcome.pages()) {
            extraction.pages.add(ClinicalDocumentPageText.create(extraction, page.pageNumber(), page.text()));
        }
        return extraction;
    }

    @PrePersist
    protected void onCreate() {
        if (this.extractedAt == null) {
            this.extractedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public ClinicalDocument getDocument() {
        return document;
    }

    public ExtractionStatus getStatus() {
        return status;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public int getPageCount() {
        return pageCount;
    }

    public ExtractionErrorCategory getErrorCategory() {
        return errorCategory;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getExtractedAt() {
        return extractedAt;
    }

    public List<ClinicalDocumentPageText> getPages() {
        return pages;
    }
}