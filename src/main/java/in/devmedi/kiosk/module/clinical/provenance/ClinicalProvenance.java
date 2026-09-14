package in.devmedi.kiosk.module.clinical.provenance;

/**
 * Provenance of a clinical datum: who or what produced it.
 *
 * <p>Every important clinical datum carries provenance so the system can
 * always answer "how do we know this?" honestly. Provenance is preserved
 * through transformations: an AI summarizer may create an {@link #AI_ASSISTED}
 * summary, but unless a physician explicitly accepts or edits it, it is never
 * relabelled as {@link #PHYSICIAN_ENTERED}.</p>
 */
public enum ClinicalProvenance {

    /** Directly reported by the patient, verbatim and traceable to their own words. */
    PATIENT_REPORTED,

    /** Extracted from an uploaded clinical document (deterministic parser or OCR). */
    DOCUMENT_EXTRACTED,

    /** Entered or confirmed by a physician in the clinical workspace. */
    PHYSICIAN_ENTERED,

    /** Derived deterministically by the system (e.g. red-flag evaluation, summaries). */
    SYSTEM_GENERATED,

    /** Produced with AI assistance; never a replacement for physician attestation. */
    AI_ASSISTED;

    public static ClinicalProvenance normalize(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (ClinicalProvenance p : values()) {
            if (p.name().equals(v)) {
                return p;
            }
        }
        return null;
    }
}