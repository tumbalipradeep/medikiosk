package in.devmedi.kiosk.module.medication.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Identifies a recognized medicine: its canonical display name and the
 * therapeutic class used by {@code medication_interaction_rules}. Seeded as
 * data so the screening set can be extended without a code change.
 */
@Entity
@Table(name = "medication_profiles")
public class MedicationProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_name", nullable = false, length = 80)
    private String canonicalName;

    @Column(name = "class_key", nullable = false, length = 40)
    private String classKey;

    @Column(name = "aliases", length = 500)
    private String aliases;

    @Column(name = "description", length = 500)
    private String description;

    protected MedicationProfile() {
    }

    public Long getId() {
        return id;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public String getClassKey() {
        return classKey;
    }

    public String getAliases() {
        return aliases;
    }

    public String getDescription() {
        return description;
    }
}