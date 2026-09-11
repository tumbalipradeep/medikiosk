package in.devmedi.kiosk.module.document.findings.entity;

import in.devmedi.kiosk.module.document.findings.model.CompletenessStatus;
import in.devmedi.kiosk.module.document.findings.model.Medication;
import in.devmedi.kiosk.module.document.findings.model.MedicationField;
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
 * One decoded medication belonging to a {@link ClinicalDocumentFindings} row.
 */
@Entity
@Table(name = "clinical_document_findings_medications")
public class ClinicalDocumentFindingsMedication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "findings_id", nullable = false)
    private ClinicalDocumentFindings findings;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "strength", length = 50)
    private String strength;

    @Column(name = "dose", length = 50)
    private String dose;

    @Column(name = "route", length = 50)
    private String route;

    @Column(name = "frequency", length = 80)
    private String frequency;

    @Column(name = "duration", length = 80)
    private String duration;

    @Column(name = "source_snippet", nullable = false, length = 500)
    private String sourceSnippet;

    @Column(name = "occurrence_index", nullable = false)
    private int occurrenceIndex;

    @Column(name = "completeness_status", nullable = false, length = 16)
    private String completenessStatus = CompletenessStatus.INCOMPLETE.name();

    @Column(name = "missing_fields", length = 120)
    private String missingFields;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount = 1;

    protected ClinicalDocumentFindingsMedication() {
    }

    public static ClinicalDocumentFindingsMedication create(ClinicalDocumentFindings findings, Medication medication) {
        ClinicalDocumentFindingsMedication entity = new ClinicalDocumentFindingsMedication();
        entity.findings = findings;
        entity.name = shorten(medication.name(), 150);
        entity.strength = shorten(medication.strength(), 50);
        entity.dose = shorten(medication.dose(), 50);
        entity.route = shorten(medication.route(), 50);
        entity.frequency = shorten(medication.frequency(), 80);
        entity.duration = shorten(medication.duration(), 80);
        entity.sourceSnippet = shorten(medication.sourceSnippet(), 500);
        entity.occurrenceIndex = medication.occurrenceIndex();
        if (medication.completeness() != null) {
            entity.completenessStatus = medication.completeness().name();
        }
        entity.missingFields = joinFields(medication.missingFields());
        entity.duplicateCount = medication.duplicateCount();
        return entity;
    }

    private static String joinFields(java.util.List<MedicationField> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        for (MedicationField field : fields) {
            if (joined.length() > 0) {
                joined.append(',');
            }
            joined.append(field.name());
        }
        return joined.toString();
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

    public String getName() {
        return name;
    }

    public String getStrength() {
        return strength;
    }

    public String getDose() {
        return dose;
    }

    public String getRoute() {
        return route;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getDuration() {
        return duration;
    }

    public String getSourceSnippet() {
        return sourceSnippet;
    }

    public int getOccurrenceIndex() {
        return occurrenceIndex;
    }

    public String getCompletenessStatus() {
        return completenessStatus;
    }

    public String getMissingFields() {
        return missingFields;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }
}