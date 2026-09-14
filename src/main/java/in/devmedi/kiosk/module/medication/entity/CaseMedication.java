package in.devmedi.kiosk.module.medication.entity;

import in.devmedi.kiosk.module.medication.enums.MedicationSource;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * The physician overlay for a single case medicine, keyed by case + normalized
 * medicine name. An active row (suppressedAt null) is a medicine the physician
 * added to the case screen; a row with suppressedAt set is a tombstone that
 * excludes that medicine name (sourced or added) from the computed list.
 */
@Entity
@Table(name = "case_medications")
public class CaseMedication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false, length = 64)
    private String caseId;

    @Column(name = "med_name", nullable = false, length = 150)
    private String medName;

    @Column(name = "dose", length = 80)
    private String dose;

    @Column(name = "frequency", length = 80)
    private String frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private MedicationSource source;

    @Column(name = "added_by")
    private Long addedBy;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    @Column(name = "suppressed_at")
    private OffsetDateTime suppressedAt;

    protected CaseMedication() {
    }

    public static CaseMedication add(String caseId, String medName, String dose, String frequency,
                                     Long addedBy, OffsetDateTime now) {
        CaseMedication row = new CaseMedication();
        row.caseId = caseId;
        row.medName = medName;
        row.dose = dose;
        row.frequency = frequency;
        row.source = MedicationSource.PHYSICIAN_ENTERED;
        row.addedBy = addedBy;
        row.addedAt = now;
        return row;
    }

    public static CaseMedication tombstone(String caseId, String medName, Long addedBy, OffsetDateTime now) {
        CaseMedication row = new CaseMedication();
        row.caseId = caseId;
        row.medName = medName;
        row.source = MedicationSource.PHYSICIAN_ENTERED;
        row.addedBy = addedBy;
        row.addedAt = now;
        row.suppressedAt = now;
        return row;
    }

    public void reactivate(String dose, String frequency, OffsetDateTime now) {
        this.dose = dose;
        this.frequency = frequency;
        this.suppressedAt = null;
        this.addedAt = now;
    }

    public void suppress(OffsetDateTime now) {
        this.suppressedAt = now;
    }

    /**
     * Clears the tombstone, bringing the medicine name back onto the case
     * screen (keeping any stored dose/frequency).
     */
    public void restore() {
        this.suppressedAt = null;
    }

    public Long getId() {
        return id;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getMedName() {
        return medName;
    }

    public String getDose() {
        return dose;
    }

    public String getFrequency() {
        return frequency;
    }

    public MedicationSource getSource() {
        return source;
    }

    public Long getAddedBy() {
        return addedBy;
    }

    public OffsetDateTime getAddedAt() {
        return addedAt;
    }

    public OffsetDateTime getSuppressedAt() {
        return suppressedAt;
    }
}