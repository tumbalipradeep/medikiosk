package in.devmedi.kiosk.module.clinical.history;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.clinical.provenance.ClinicalProvenance;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One structured clinical datum in a patient's longitudinal history.
 *
 * <p>Uniqueness is enforced on {@code (patient_user_id, category, concept_key)}
 * so recording the same deterministic item twice is an idempotent update
 * rather than a duplicate row. The provenance column preserves how the datum
 * was produced and is never silently upgraded (see {@link ClinicalProvenance}).</p>
 */
@Entity
@Table(name = "clinical_history_items",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"patient_user_id", "category", "concept_key"},
                name = "uq_history_patient_category_key"))
public class ClinicalHistoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_user_id", nullable = false)
    private User patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ClinicalHistoryCategory category;

    @Column(name = "concept_key", nullable = false, length = 120)
    private String conceptKey;

    @Column(nullable = false, length = 300)
    private String label;

    @Column(nullable = false, length = 4000)
    private String value;

    @Column(length = 2000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ClinicalProvenance provenance;

    @Column(name = "source_label", length = 200)
    private String sourceLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClinicalHistoryItem() {
    }

    public ClinicalHistoryItem(User patient,
                               ClinicalHistoryCategory category,
                               String conceptKey,
                               String label,
                               String value,
                               String note,
                               ClinicalProvenance provenance,
                               String sourceLabel,
                               User recordedBy) {
        this.patient = patient;
        this.category = category;
        this.conceptKey = conceptKey;
        this.label = label;
        this.value = value;
        this.note = note;
        this.provenance = provenance == null ? ClinicalProvenance.PATIENT_REPORTED : provenance;
        this.sourceLabel = sourceLabel;
        this.recordedBy = recordedBy;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.recordedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getPatient() {
        return patient;
    }

    public ClinicalHistoryCategory getCategory() {
        return category;
    }

    public String getConceptKey() {
        return conceptKey;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public ClinicalProvenance getProvenance() {
        return provenance;
    }

    public void setProvenance(ClinicalProvenance provenance) {
        this.provenance = provenance;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public User getRecordedBy() {
        return recordedBy;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}