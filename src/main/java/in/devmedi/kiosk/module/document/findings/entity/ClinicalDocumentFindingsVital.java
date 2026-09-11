package in.devmedi.kiosk.module.document.findings.entity;

import in.devmedi.kiosk.module.document.findings.model.VitalSign;
import in.devmedi.kiosk.module.document.findings.model.VitalType;
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
import jakarta.persistence.Table;

/**
 * One decoded vital sign belonging to a {@link ClinicalDocumentFindings} row.
 */
@Entity
@Table(name = "clinical_document_findings_vitals")
public class ClinicalDocumentFindingsVital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "findings_id", nullable = false)
    private ClinicalDocumentFindings findings;

    @Enumerated(EnumType.STRING)
    @Column(name = "vital_type", nullable = false, length = 30)
    private VitalType type;

    @Column(name = "value", nullable = false, length = 50)
    private String value;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "source_snippet", nullable = false, length = 500)
    private String sourceSnippet;

    @Column(name = "occurrence_index", nullable = false)
    private int occurrenceIndex;

    protected ClinicalDocumentFindingsVital() {
    }

    public static ClinicalDocumentFindingsVital create(ClinicalDocumentFindings findings, VitalSign vital) {
        ClinicalDocumentFindingsVital entity = new ClinicalDocumentFindingsVital();
        entity.findings = findings;
        entity.type = vital.type();
        entity.value = vital.value();
        entity.unit = vital.unit();
        entity.sourceSnippet = shorten(vital.sourceSnippet(), 500);
        entity.occurrenceIndex = vital.occurrenceIndex();
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

    public VitalType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public String getSourceSnippet() {
        return sourceSnippet;
    }

    public int getOccurrenceIndex() {
        return occurrenceIndex;
    }
}