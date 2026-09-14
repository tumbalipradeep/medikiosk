package in.devmedi.kiosk.module.clinical.adaptive;

import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Versioned, declarative question bank for the complete-clinical-history
 * conversation.
 *
 * <p>The bank is a plain data structure — no if/else conversation logic lives
 * here. Each entry declares what to ask, in which history category it is
 * stored, its priority, and the keywords that make it applicable given what the
 * patient already said (triggered by earlier answers). The planner does the
 * selection; this class only owns the data.</p>
 */
@Component
public class CompleteHistoryQuestionBank {

    /** Traceable version of the bank contents. */
    public static final String VERSION = "complete-history-v1";

    private static final List<AdaptiveHistoryQuestion> QUESTIONS = List.of(
            // Past medical history (screening + triggering detail)
            q("pmh_chronic_illness", ClinicalHistoryCategory.PAST_MEDICAL, "chronic_illness",
                    "Do you have any long-term or chronic illness such as diabetes, hypertension, asthma, thyroid disease, or any other condition?",
                    true, 110, List.of(), null, true),
            q("pmh_diabetes", ClinicalHistoryCategory.PAST_MEDICAL, "diabetes",
                    "Please tell me about your diabetes — how long have you had it and how do you manage it?",
                    false, 111, List.of("diabetes", "sugar"), "pmh_chronic_illness", false),
            q("pmh_hypertension", ClinicalHistoryCategory.PAST_MEDICAL, "hypertension",
                    "Please tell me about your blood pressure — how long have you had it and are you on treatment?",
                    false, 112, List.of("blood pressure", "hypertension", "pressure"), "pmh_chronic_illness", false),
            q("pmh_asthma", ClinicalHistoryCategory.PAST_MEDICAL, "asthma",
                    "Please tell me about your asthma — how is it usually?",
                    false, 113, List.of("asthma"), "pmh_chronic_illness", false),
            q("pmh_thyroid", ClinicalHistoryCategory.PAST_MEDICAL, "thyroid",
                    "Please tell me about your thyroid condition and its treatment.",
                    false, 114, List.of("thyroid"), "pmh_chronic_illness", false),
            q("pmh_previous_admission", ClinicalHistoryCategory.PAST_MEDICAL, "previous_admission",
                    "Have you ever been admitted to a hospital? Please describe why and when.",
                    false, 120, List.of("admitted", "hospital", "admission"), null, false),
            q("pmh_other_conditions", ClinicalHistoryCategory.PAST_MEDICAL, "other_conditions",
                    "Are there any other past illnesses or conditions I should know about?",
                    false, 130, List.of(), null, false),

            // Past surgical history
            q("psh_any_surgery", ClinicalHistoryCategory.PAST_SURGICAL, "any_surgery",
                    "Have you ever had any surgery or operation?",
                    true, 210, List.of(), null, true),
            q("psh_surgery_detail", ClinicalHistoryCategory.PAST_SURGICAL, "surgery_detail",
                    "Please tell me about the surgery — what was it, when, and any complications after it?",
                    false, 211, List.of(), "psh_any_surgery", false),

            // Medication history
            q("med_current", ClinicalHistoryCategory.MEDICATIONS, "current_medications",
                    "Do you currently take any medicines — prescribed, over-the-counter, or herbal?",
                    true, 310, List.of(), null, true),
            q("med_list", ClinicalHistoryCategory.MEDICATIONS, "medication_list",
                    "Please list your medicines one by one — name, dose, how often, and how long you have taken each.",
                    false, 311, List.of(), "med_current", false),
            q("med_injections", ClinicalHistoryCategory.MEDICATIONS, "injections",
                    "Do you take any injections for your conditions?",
                    false, 312, List.of("injection", "insulin"), "med_current", false),

            // Allergies
            q("allg_any", ClinicalHistoryCategory.ALLERGIES, "any_allergies",
                    "Do you have any allergies or bad reactions to medicines, foods, or anything else?",
                    true, 410, List.of(), null, true),
            q("allg_detail", ClinicalHistoryCategory.ALLERGIES, "allergy_detail",
                    "Please describe the allergy — what causes it and what reaction you get.",
                    false, 411, List.of("allergy", "allergic", "reaction", "rash", "itching", "penicillin"), "allg_any", false),

            // Family history
            q("fam_hereditary", ClinicalHistoryCategory.FAMILY, "family_illnesses",
                    "Does anyone in your close family (parents, siblings, children) have a condition you are concerned could run in the family?",
                    true, 510, List.of(), null, true),
            q("fam_detail", ClinicalHistoryCategory.FAMILY, "family_detail",
                    "Please tell me about those family members and their conditions.",
                    false, 511, List.of("father", "mother", "brother", "sister", "son", "daughter",
                            "family", "heart", "cancer", "diabetes", "hypertension"), "fam_hereditary", false),

            // Personal and social history
            q("pers_occupation", ClinicalHistoryCategory.PERSONAL_SOCIAL, "occupation",
                    "What do you do for a living?",
                    true, 610, List.of(), null, false),
            q("pers_tobacco", ClinicalHistoryCategory.PERSONAL_SOCIAL, "tobacco",
                    "Do you use any tobacco or smoke?",
                    true, 620, List.of(), null, false),
            q("pers_alcohol", ClinicalHistoryCategory.PERSONAL_SOCIAL, "alcohol",
                    "Do you drink alcohol?",
                    true, 630, List.of(), null, false),
            q("pers_living", ClinicalHistoryCategory.PERSONAL_SOCIAL, "living_situation",
                    "Who do you live with at home?",
                    false, 640, List.of(), null, false),

            // Review of systems (screeners)
            q("ros_constitutional", ClinicalHistoryCategory.REVIEW_OF_SYSTEMS, "constitutional",
                    "Apart from your main problem, have you had fever, weight change, fatigue, or night sweats recently?",
                    true, 710, List.of(), null, false),
            q("ros_cardiovascular", ClinicalHistoryCategory.REVIEW_OF_SYSTEMS, "cardiovascular",
                    "Have you had chest pain, palpitations, breathlessness on exertion, or swelling of your feet?",
                    true, 720, List.of(), null, false),
            q("ros_respiratory", ClinicalHistoryCategory.REVIEW_OF_SYSTEMS, "respiratory",
                    "Have you had cough, wheeze, or breathing difficulty?",
                    true, 730, List.of(), null, false),
            q("ros_gastrointestinal", ClinicalHistoryCategory.REVIEW_OF_SYSTEMS, "gastrointestinal",
                    "Have you had abdominal pain, acidity, changes in appetite, digestion, or bowel habits?",
                    true, 740, List.of(), null, false),
            q("ros_neurological", ClinicalHistoryCategory.REVIEW_OF_SYSTEMS, "neurological",
                    "Have you had headaches, dizziness, tingling, or weakness in any part of your body?",
                    true, 750, List.of(), null, false),
            q("ros_genitourinary", ClinicalHistoryCategory.REVIEW_OF_SYSTEMS, "genitourinary",
                    "Have you had any burning while passing urine, increased frequency, or difficulty passing urine?",
                    true, 760, List.of(), null, false),
            q("ros_musculoskeletal", ClinicalHistoryCategory.REVIEW_OF_SYSTEMS, "musculoskeletal",
                    "Have you had joint pain, stiffness, or muscle aches?",
                    true, 770, List.of(), null, false));

    private final List<AdaptiveHistoryQuestion> bank;

    public CompleteHistoryQuestionBank() {
        this.bank = List.copyOf(QUESTIONS);
    }

    public List<AdaptiveHistoryQuestion> questions() {
        return bank;
    }

    public AdaptiveHistoryQuestion question(String id) {
        return bank.stream().filter(q -> q.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown adaptive question id: " + id));
    }

    public static AdaptiveHistoryQuestion q(String id,
                                            ClinicalHistoryCategory category,
                                            String conceptKey,
                                            String text,
                                            boolean required,
                                            int priority,
                                            List<String> triggers,
                                            String parent,
                                            boolean negativeTermination) {
        return new AdaptiveHistoryQuestion(id, category, conceptKey, text, required, priority,
                triggers, parent, negativeTermination);
    }
}