package in.devmedi.kiosk.module.clinical.history;

import java.util.Locale;

/**
 * Structured clinical history categories collected during a complete clinical
 * history. The "do not implement as one giant fixed questionnaire" requirement
 * is satisfied by the adaptive conversation layer working against these
 * categories; each category is a coherent, independently collectable section.
 */
public enum ClinicalHistoryCategory {

    /** The main problem or symptom that brought the patient here today. */
    CHIEF_COMPLAINT,

    /** History of present illness (site, onset, character, radiation, associated symptoms, timing, aggravating/relieving factors, severity). */
    HPI,

    /** Past medical history (diagnoses, chronic conditions, previous admissions). */
    PAST_MEDICAL,

    /** Past surgical history (operations, procedures, dates, complications). */
    PAST_SURGICAL,

    /** Current medication history (prescribed, OTC, herbal; name, strength, frequency, duration). */
    MEDICATIONS,

    /** Allergies and adverse drug reactions (substance, reaction, severity). */
    ALLERGIES,

    /** Family history (relevant conditions in first- and second-degree relatives). */
    FAMILY,

    /** Personal and social history (occupation, habits, tobacco/alcohol, living situation). */
    PERSONAL_SOCIAL,

    /** Review of systems (constitutional, each body system screened briefly). */
    REVIEW_OF_SYSTEMS,

    /** AYUSH Dashavidha Pariksha parameters (Prakriti, Vikriti, Sara, ...). */
    DASHAVIDHA,

    /** AYUSH Ahara-Vihara dietary and lifestyle history. */
    AHARA_VIHARA,

    /** Vitals / measurements (from documents or device input). */
    VITALS;

    /** @return a human-readable label e.g. {@code "Past medical history"}. */
    public String readableName() {
        String lower = name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    public static ClinicalHistoryCategory normalize(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        for (ClinicalHistoryCategory c : values()) {
            if (c.name().equals(v)) {
                return c;
            }
        }
        return null;
    }
}