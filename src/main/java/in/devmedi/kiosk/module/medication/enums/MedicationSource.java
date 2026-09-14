package in.devmedi.kiosk.module.medication.enums;

/**
 * Where a medicine on the case screen came from. Sourced medicines are always
 * re-derived from clinical history and document findings; the physician overlay
 * rows are stored explicitly so they survive across page reloads.
 */
public enum MedicationSource {

    PATIENT_HISTORY,
    DOCUMENT_FINDING,
    PHYSICIAN_ENTERED
}