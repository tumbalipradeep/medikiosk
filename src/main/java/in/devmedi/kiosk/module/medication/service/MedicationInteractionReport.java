package in.devmedi.kiosk.module.medication.service;

import in.devmedi.kiosk.module.medication.enums.MedicationInteractionSeverity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The complete medication interaction screening for one case.
 *
 * @param caseId        the case identifier
 * @param medications   the active medicines on the case screen (sourced + physician-entered)
 * @param interactions  the screened interactions between those medicines
 * @param overallSeverity the most severe level found, or null when there are no interactions
 * @param analyzedAt    when the report was assembled
 */
public record MedicationInteractionReport(
        String caseId,
        List<MedicationView> medications,
        List<MedicationInteractionView> interactions,
        MedicationInteractionSeverity overallSeverity,
        OffsetDateTime analyzedAt) {

    public boolean hasInteractions() {
        return !interactions.isEmpty();
    }
}