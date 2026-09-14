package in.devmedi.kiosk.module.clinical.history;

import in.devmedi.kiosk.module.clinical.provenance.ClinicalProvenance;

/**
 * A single datum to be batch-recorded into the clinical history.
 *
 * @param category   history category (chief complaint, medications, etc.)
 * @param conceptKey unique key within the category, e.g. {@code "chief_complaint"}
 * @param label      human-readable name, e.g. {@code "Chief complaint"}
 * @param value      the value, e.g. {@code "Persistent cough for 2 weeks"}
 * @param note       optional free-text note
 * @param provenance how this datum was produced
 * @param sourceLabel source label, e.g. {@code "kiosk intake"}
 */
public record HistoryDatum(
        ClinicalHistoryCategory category,
        String conceptKey,
        String label,
        String value,
        String note,
        ClinicalProvenance provenance,
        String sourceLabel
) {
    public HistoryDatum {
    }

    public HistoryDatum(ClinicalHistoryCategory category, String conceptKey,
                        String label, String value) {
        this(category, conceptKey, label, value, null, ClinicalProvenance.PATIENT_REPORTED, "kiosk intake");
    }

    public HistoryDatum(ClinicalHistoryCategory category, String conceptKey,
                        String label, String value, ClinicalProvenance provenance) {
        this(category, conceptKey, label, value, null, provenance, "kiosk intake");
    }
}