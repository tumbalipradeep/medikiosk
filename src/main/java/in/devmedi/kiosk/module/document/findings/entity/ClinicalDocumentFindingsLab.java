package in.devmedi.kiosk.module.document.findings.entity;

import in.devmedi.kiosk.module.document.findings.model.AbnormalityStatus;
import in.devmedi.kiosk.module.document.findings.model.LabResult;
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
 * One decoded laboratory result belonging to a {@link ClinicalDocumentFindings}
 * row.
 */
@Entity
@Table(name = "clinical_document_findings_labs")
public class ClinicalDocumentFindingsLab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "findings_id", nullable = false)
    private ClinicalDocumentFindings findings;

    @Column(name = "test_name", nullable = false, length = 120)
    private String testName;

    @Column(name = "raw_value", length = 50)
    private String rawValue;

    @Column(name = "value", nullable = false, length = 50)
    private String value;

    @Column(name = "unit", length = 60)
    private String unit;

    @Column(name = "reference_range", length = 120)
    private String referenceRange;

    @Column(name = "specimen_date", length = 20)
    private String specimenDate;

    @Column(name = "source_snippet", nullable = false, length = 500)
    private String sourceSnippet;

    @Column(name = "occurrence_index", nullable = false)
    private int occurrenceIndex;

    @Column(name = "abnormality_status", nullable = false, length = 16)
    private String abnormalityStatus = AbnormalityStatus.UNKNOWN.name();

    protected ClinicalDocumentFindingsLab() {
    }

    public static ClinicalDocumentFindingsLab create(ClinicalDocumentFindings findings, LabResult lab) {
        ClinicalDocumentFindingsLab entity = new ClinicalDocumentFindingsLab();
        entity.findings = findings;
        entity.testName = shorten(lab.testName(), 120);
        entity.rawValue = shorten(lab.rawValue(), 50);
        entity.value = lab.value();
        entity.unit = shorten(lab.unit(), 60);
        entity.referenceRange = shorten(lab.referenceRange(), 120);
        entity.specimenDate = shorten(lab.specimenDate(), 20);
        entity.sourceSnippet = shorten(lab.sourceSnippet(), 500);
        entity.occurrenceIndex = lab.occurrenceIndex();
        if (lab.abnormalityStatus() != null) {
            entity.abnormalityStatus = lab.abnormalityStatus().name();
        }
        return entity;
    }

    private static String shorten(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public String getTestName() {
        return testName;
    }

    public String getRawValue() {
        return rawValue;
    }

    public String getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public String getReferenceRange() {
        return referenceRange;
    }

    public String getSpecimenDate() {
        return specimenDate;
    }

    public String getSourceSnippet() {
        return sourceSnippet;
    }

    public int getOccurrenceIndex() {
        return occurrenceIndex;
    }

    public String getAbnormalityStatus() {
        return abnormalityStatus;
    }
}