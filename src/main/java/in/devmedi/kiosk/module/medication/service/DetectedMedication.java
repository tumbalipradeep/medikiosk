package in.devmedi.kiosk.module.medication.service;

import in.devmedi.kiosk.module.medication.enums.MedicationSource;

/**
 * One detected medicine after normalization, before suppression is applied.
 *
 * @param sourceChunk  the raw text (or findings medicine name) the medicine was detected from
 * @param displayName  the name shown to the physician (canonical when known, otherwise the raw chunk)
 * @param canonicalName the canonical profile name, or null when the medicine is unrecognized
 * @param classKey     the therapeutic class key, or null when unrecognized
 * @param source       where the medicine came from
 */
public record DetectedMedication(
        String sourceChunk,
        String displayName,
        String canonicalName,
        String classKey,
        MedicationSource source) {
}