package in.devmedi.kiosk.module.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Page-level extracted text for a {@link ClinicalDocumentExtraction}. Kept in a
 * separate table so page ordering is explicitly queryable and large text bodies
 * do not overload the extraction metadata row.
 */
@Entity
@Table(name = "clinical_document_page_texts")
public class ClinicalDocumentPageText {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extraction_id", nullable = false)
    private ClinicalDocumentExtraction extraction;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "page_text", nullable = false, columnDefinition = "TEXT")
    private String pageText;

    protected ClinicalDocumentPageText() {
    }

    public static ClinicalDocumentPageText create(ClinicalDocumentExtraction extraction, int pageNumber, String pageText) {
        ClinicalDocumentPageText page = new ClinicalDocumentPageText();
        page.extraction = extraction;
        page.pageNumber = pageNumber;
        page.pageText = pageText;
        return page;
    }

    public Long getId() {
        return id;
    }

    public ClinicalDocumentExtraction getExtraction() {
        return extraction;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getPageText() {
        return pageText;
    }
}