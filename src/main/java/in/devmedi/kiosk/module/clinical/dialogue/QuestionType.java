package in.devmedi.kiosk.module.clinical.dialogue;

/**
 * The type of a clinical dialogue question. Types follow the SOCRATES
 * case-taking facets for the chief complaint and history of present illness.
 */
public enum QuestionType {

    SYMPTOM_PROBLEM,
    ONSET,
    PROVOCATION_PALLIATION,
    QUALITY,
    REGION_RADIATION,
    SEVERITY,
    TIMING_DURATION
}