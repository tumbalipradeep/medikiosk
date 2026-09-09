package in.devmedi.kiosk.module.physician.service;

import in.devmedi.kiosk.module.clinical.ayush.AharaViharaQuestion;
import in.devmedi.kiosk.module.clinical.ayush.AharaViharaQuestionPlanner;
import in.devmedi.kiosk.module.clinical.ayush.DashavidhaQuestion;
import in.devmedi.kiosk.module.clinical.ayush.DashavidhaQuestionPlanner;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalQuestion;
import in.devmedi.kiosk.module.clinical.dialogue.IntakeQuestion;
import in.devmedi.kiosk.module.clinical.dialogue.QuestionPlanner;
import in.devmedi.kiosk.module.clinical.summary.ClinicalSummary;
import in.devmedi.kiosk.module.clinical.summary.ClinicalSummaryBuilder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic demo source for the physician review page.
 *
 * <p>This checkpoint intentionally has no persistence: the page renders a fixed,
 * clearly illustrative in-memory conversation built from the same planners the
 * patient intake uses, and summarized with the shared
 * {@link ClinicalSummaryBuilder}. Answers are demo text only — nothing is
 * diagnosed, scored, interpreted, or stored.</p>
 */
@Service
public class DemoPhysicianReviewService {

    private final ClinicalSummaryBuilder summaryBuilder;
    private final QuestionPlanner questionPlanner;
    private final DashavidhaQuestionPlanner dashavidhaQuestionPlanner;
    private final AharaViharaQuestionPlanner aharaViharaQuestionPlanner;

    public DemoPhysicianReviewService(ClinicalSummaryBuilder summaryBuilder,
                                      QuestionPlanner questionPlanner,
                                      DashavidhaQuestionPlanner dashavidhaQuestionPlanner,
                                      AharaViharaQuestionPlanner aharaViharaQuestionPlanner) {
        this.summaryBuilder = summaryBuilder;
        this.questionPlanner = questionPlanner;
        this.dashavidhaQuestionPlanner = dashavidhaQuestionPlanner;
        this.aharaViharaQuestionPlanner = aharaViharaQuestionPlanner;
    }

    /**
     * @return a deterministic demo clinical summary for the review page
     */
    public ClinicalSummary demoSummary() {
        Map<String, String> demoAnswers = demoAnswers();
        ClinicalConversationResult result = new ClinicalConversationResult();

        for (ClinicalQuestion question : questionPlanner.questions()) {
            result.record(ClinicalAnswer.from(IntakeQuestion.fromClinical(question),
                    demoAnswers.get(question.id())));
        }
        for (DashavidhaQuestion question : dashavidhaQuestionPlanner.questions()) {
            result.record(ClinicalAnswer.from(IntakeQuestion.fromDashavidha(question),
                    demoAnswers.get(question.id())));
        }
        for (AharaViharaQuestion question : aharaViharaQuestionPlanner.questions()) {
            result.record(ClinicalAnswer.from(IntakeQuestion.fromAharaVihara(question),
                    demoAnswers.get(question.id())));
        }
        return summaryBuilder.summarize(result);
    }

    private Map<String, String> demoAnswers() {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("chief_complaint_symptom",
                "Severe headache, mainly on the right side, since the day before yesterday.");
        answers.put("hpi_onset", "It started gradually about two days ago and is worst in the morning.");
        answers.put("hpi_provocation_palliation",
                "Bright light and loud noise make it worse; lying down in a dark, quiet room helps.");
        answers.put("hpi_quality", "Throbbing, pressing pain.");
        answers.put("hpi_region_radiation", "Right temple spreading to behind the right eye; no spread to the neck.");
        answers.put("hpi_severity", "Around 7 out of 10 at its worst.");
        answers.put("hpi_timing_duration", "Constant with occasional sharp spikes; each episode lasts several hours.");

        answers.put("dashavidha_prakriti",
                "Lean build with dry skin; tends to feel cold most of the time.");
        answers.put("dashavidha_vikriti",
                "More irritable and tired than usual since the headache started.");
        answers.put("dashavidha_sara",
                "Generally good stamina; slightly low over the last few days.");
        answers.put("dashavidha_samhanana", "Slim to medium frame and moderately firm build.");
        answers.put("dashavidha_pramana",
                "Height and weight close to what I consider normal for me.");
        answers.put("dashavidha_satmya",
                "Warm, home-cooked food suits me best; cold drinks do not agree with me.");
        answers.put("dashavidha_sattva", "Usually calm, but I become a little anxious under stress or pain.");
        answers.put("dashavidha_ahara_shakti", "Appetite is moderate and digestion is usually fine.");
        answers.put("dashavidha_vyayama_shakti", "I can walk briskly for about 30 minutes before tiring.");
        answers.put("dashavidha_vaya", "Middle age.");

        answers.put("ahara_vihara_ahara", "Two to three meals a day, mostly rice, dal, vegetables, and salad.");
        answers.put("ahara_vihara_meal_pattern", "Breakfast around 8 am, lunch at 1 pm, and an early dinner around 7 pm.");
        answers.put("ahara_vihara_appetite", "Moderate appetite; slightly reduced when the headache flares.");
        answers.put("ahara_vihara_hydration", "About six to eight glasses of water a day, plus a cup of tea.");
        answers.put("ahara_vihara_sleep", "About seven hours; light and often interrupted by the headache.");
        answers.put("ahara_vihara_physical_activity", "A short morning walk and light household work.");
        answers.put("ahara_vihara_daily_routine", "Wake around 6 am, desk work during the day, and reading in the evening.");
        answers.put("ahara_vihara_habits", "Occasional evening tea; no smoking and no alcohol.");
        return answers;
    }
}