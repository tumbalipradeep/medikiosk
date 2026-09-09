package in.devmedi.kiosk.module.clinical.dialogue;

import in.devmedi.kiosk.module.clinical.ayush.AharaViharaQuestion;
import in.devmedi.kiosk.module.clinical.ayush.DashavidhaQuestion;

/**
 * Unified, render-ready description of one conversation question, regardless of
 * which planner it belongs to.
 *
 * <p>HPI/SOCRATES questions carry their clinical section and SOCRATES facet;
 * Dashavidha questions use the fixed section {@value #DASHAVIDHA_SECTION} and
 * the assessed Dashavidha parameter as the type; Ahara-Vihara questions use the
 * fixed section {@value #AHARA_VIHARA_SECTION} and the assessed diet/lifestyle
 * parameter as the type.</p>
 *
 * @param id       stable question id
 * @param section  section this question belongs to (HPI section, Dashavidha, or Ahara-Vihara)
 * @param type     question type (SOCRATES facet, Dashavidha parameter, or Ahara-Vihara parameter)
 * @param text     question text shown to the patient
 * @param required whether the answer is required
 * @param order    ordering/priority within its own sequence
 */
public record IntakeQuestion(String id,
                             String section,
                             String type,
                             String text,
                             boolean required,
                             int order) {

    public static final String DASHAVIDHA_SECTION = "DASHAVIDHA";
    public static final String AHARA_VIHARA_SECTION = "AHARA_VIHARA";

    public static IntakeQuestion fromClinical(ClinicalQuestion question) {
        return new IntakeQuestion(question.id(),
                question.section().name(),
                question.type().name(),
                question.text(),
                question.required(),
                question.order());
    }

    public static IntakeQuestion fromDashavidha(DashavidhaQuestion question) {
        return new IntakeQuestion(question.id(),
                DASHAVIDHA_SECTION,
                question.parameter().name(),
                question.text(),
                question.required(),
                question.order());
    }

    public static IntakeQuestion fromAharaVihara(AharaViharaQuestion question) {
        return new IntakeQuestion(question.id(),
                AHARA_VIHARA_SECTION,
                question.parameter().name(),
                question.text(),
                question.required(),
                question.order());
    }
}