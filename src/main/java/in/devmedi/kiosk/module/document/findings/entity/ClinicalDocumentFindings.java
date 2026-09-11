package in.devmedi.kiosk.module.document.findings.entity;

import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.findings.model.EncounterMetadata;
import in.devmedi.kiosk.module.document.findings.model.InteractionAnalysisStatus;
import in.devmedi.kiosk.module.document.findings.model.PatientIdentifiers;
import in.devmedi.kiosk.module.document.findings.model.StructuredFindings;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted structured findings for one document extraction (1:1).
 *
 * <p>Patient/encounter identity is stored as plain columns; the repeating
 * decoded values (vitals, lab results, medications) are stored as ordered
 * child rows so occurrence ordering and raw source snippets survive
 * persistence.</p>
 */
@Entity
@Table(name = "clinical_document_findings")
public class ClinicalDocumentFindings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extraction_id", nullable = false, unique = true)
    private ClinicalDocumentExtraction extraction;

    @Column(name = "patient_name", length = 255)
    private String patientName;

    @Column(name = "patient_dob", length = 20)
    private String patientDob;

    @Column(name = "patient_sex", length = 20)
    private String patientSex;

    @Column(name = "patient_mrn", length = 64)
    private String patientMrn;

    @Column(name = "report_date", length = 20)
    private String reportDate;

    @Column(name = "encounter_date", length = 20)
    private String encounterDate;

    @Column(name = "facility", length = 255)
    private String facility;

    @Column(name = "clinician", length = 255)
    private String clinician;

    @Column(name = "report_type", length = 255)
    private String reportType;

    @Column(name = "interaction_analysis_status", nullable = false, length = 32)
    private String interactionAnalysisStatus = InteractionAnalysisStatus.NOT_AVAILABLE.name();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "findings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("occurrenceIndex ASC")
    private List<ClinicalDocumentFindingsVital> vitals = new ArrayList<>();

    @OneToMany(mappedBy = "findings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("occurrenceIndex ASC")
    private List<ClinicalDocumentFindingsLab> labResults = new ArrayList<>();

    @OneToMany(mappedBy = "findings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("occurrenceIndex ASC")
    private List<ClinicalDocumentFindingsMedication> medications = new ArrayList<>();

    protected ClinicalDocumentFindings() {
    }

    public static ClinicalDocumentFindings create(ClinicalDocumentExtraction extraction, StructuredFindings findings) {
        ClinicalDocumentFindings entity = new ClinicalDocumentFindings();
        entity.extraction = extraction;

        PatientIdentifiers patient = findings.patient();
        if (patient != null) {
            entity.patientName = shorten(patient.name(), 255);
            entity.patientDob = shorten(patient.dateOfBirth(), 20);
            entity.patientSex = shorten(patient.sex(), 20);
            entity.patientMrn = shorten(patient.mrn(), 64);
        }
        if (findings.interactionAnalysisStatus() != null) {
            entity.interactionAnalysisStatus = findings.interactionAnalysisStatus().name();
        }
        EncounterMetadata encounter = findings.encounter();
        if (encounter != null) {
            entity.reportDate = shorten(encounter.reportDate(), 20);
            entity.encounterDate = shorten(encounter.encounterDate(), 20);
            entity.facility = shorten(encounter.facility(), 255);
            entity.clinician = shorten(encounter.clinician(), 255);
            entity.reportType = shorten(encounter.reportType(), 255);
        }

        for (var vital : findings.vitals()) {
            entity.vitals.add(ClinicalDocumentFindingsVital.create(entity, vital));
        }
        for (var lab : findings.labResults()) {
            entity.labResults.add(ClinicalDocumentFindingsLab.create(entity, lab));
        }
        for (var med : findings.medications()) {
            entity.medications.add(ClinicalDocumentFindingsMedication.create(entity, med));
        }
        return entity;
    }

    private static String shorten(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public ClinicalDocumentExtraction getExtraction() {
        return extraction;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getPatientDob() {
        return patientDob;
    }

    public String getPatientSex() {
        return patientSex;
    }

    public String getPatientMrn() {
        return patientMrn;
    }

    public String getReportDate() {
        return reportDate;
    }

    public String getEncounterDate() {
        return encounterDate;
    }

    public String getFacility() {
        return facility;
    }

    public String getClinician() {
        return clinician;
    }

    public String getReportType() {
        return reportType;
    }

    public String getInteractionAnalysisStatus() {
        return interactionAnalysisStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ClinicalDocumentFindingsVital> getVitals() {
        return vitals;
    }

    public List<ClinicalDocumentFindingsLab> getLabResults() {
        return labResults;
    }

    public List<ClinicalDocumentFindingsMedication> getMedications() {
        return medications;
    }
}