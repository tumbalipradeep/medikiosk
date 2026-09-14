package in.devmedi.kiosk.module.medication.entity;

import in.devmedi.kiosk.module.medication.enums.MedicationInteractionSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One interaction rule. Each side of the pair is either a canonical drug name
 * (e.g. {@code Warfarin}) or a therapeutic class key (e.g. {@code NSAID}).
 * The engine matches pairs symmetrically, so the stored order is irrelevant.
 */
@Entity
@Table(name = "medication_interaction_rules")
public class MedicationInteractionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_a", nullable = false, length = 80)
    private String groupA;

    @Column(name = "group_b", nullable = false, length = 80)
    private String groupB;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private MedicationInteractionSeverity severity;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "guidance", nullable = false, length = 1000)
    private String guidance;

    protected MedicationInteractionRule() {
    }

    public Long getId() {
        return id;
    }

    public String getGroupA() {
        return groupA;
    }

    public String getGroupB() {
        return groupB;
    }

    public MedicationInteractionSeverity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getGuidance() {
        return guidance;
    }
}