package in.devmedi.kiosk.module.medication.service;

import in.devmedi.kiosk.module.medication.enums.MedicationInteractionSeverity;

/**
 * One screened interaction between two medicines on the case.
 *
 * @param drugA       the first medicine's display name
 * @param drugB       the second medicine's display name
 * @param severity    the screening severity level
 * @param title       short human-readable title of the rule
 * @param description plain-language explanation
 * @param guidance    monitoring / action guidance for the physician
 */
public record MedicationInteractionView(
        String drugA,
        String drugB,
        MedicationInteractionSeverity severity,
        String title,
        String description,
        String guidance) {

    public String label() {
        return drugA + " + " + drugB;
    }
}