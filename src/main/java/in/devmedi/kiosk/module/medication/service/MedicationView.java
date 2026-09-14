package in.devmedi.kiosk.module.medication.service;

import in.devmedi.kiosk.module.medication.enums.MedicationSource;

/**
 * A medicine row in the case screening report.
 *
 * @param rowId    the {@code case_medications} row id for physician-added medicines (null for sourced rows)
 * @param displayName the name as shown
 * @param classKey  the therapeutic class, or null when unrecognized
 * @param dose      dose text for physician-added medicines, if any
 * @param frequency frequency text for physician-added medicines, if any
 */
public record MedicationView(
        Long rowId,
        String displayName,
        String classKey,
        String dose,
        String frequency,
        MedicationSource source,
        boolean physicianAdded) {
}