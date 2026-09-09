package in.devmedi.kiosk.module.clinical.redflag;

import in.devmedi.kiosk.module.clinical.dialogue.ClinicalQuestion;
import in.devmedi.kiosk.module.clinical.dialogue.QuestionPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedFlagEvaluatorTests {

    private final RedFlagEvaluator evaluator = new RedFlagEvaluator();
    private final ClinicalQuestion question = new QuestionPlanner().firstQuestion();

    private List<RedFlag> flags(String answer) {
        return evaluator.evaluate(question, answer);
    }

    private RedFlag only(String answer) {
        List<RedFlag> result = flags(answer);
        assertThat(result).hasSize(1);
        return result.get(0);
    }

    @Test
    void ordinaryAnswerProducesNoRedFlags() {
        assertThat(flags("I have had a mild headache and a bit of fever for two days.")).isEmpty();
        assertThat(flags("I woke up with a sore throat this morning.")).isEmpty();
        assertThat(evaluator.overallSeverity(flags("I have a mild headache."))).isEqualTo(RedFlagSeverity.NONE);
    }

    @Test
    void breathingPatternRaisesUrgentFlag() {
        RedFlag flag = only("I cannot breathe.");
        assertThat(flag.id()).isEqualTo("red_flag_breathing");
        assertThat(flag.severity()).isEqualTo(RedFlagSeverity.URGENT);
        assertThat(flag.title()).isNotBlank();
        assertThat(flag.message()).isNotBlank();

        assertThat(only("It is severe difficulty breathing.").id()).isEqualTo("red_flag_breathing");
        assertThat(only("I can't breathe at all.").id()).isEqualTo("red_flag_breathing");
    }

    @Test
    void consciousnessPatternRaisesUrgentFlag() {
        assertThat(only("I fainted this morning.").id()).isEqualTo("red_flag_consciousness");
        assertThat(only("I lost consciousness yesterday.").id()).isEqualTo("red_flag_consciousness");
        assertThat(only("He was unconscious.").id()).isEqualTo("red_flag_consciousness");
    }

    @Test
    void chestPainPatternRaisesUrgentFlag() {
        assertThat(only("I have crushing chest pain.").id()).isEqualTo("red_flag_chest_pain");
        assertThat(only("There is severe chest pressure.").id()).isEqualTo("red_flag_chest_pain");
    }

    @Test
    void bleedingPatternRaisesUrgentFlag() {
        assertThat(only("I have uncontrolled heavy bleeding.").id()).isEqualTo("red_flag_bleeding");
        assertThat(only("The wound is bleeding profusely.").id()).isEqualTo("red_flag_bleeding");
    }

    @Test
    void strokePatternRaisesUrgentFlag() {
        assertThat(only("I cannot move my left arm suddenly.").id()).isEqualTo("red_flag_stroke");
        assertThat(only("There is sudden weakness in my right leg.").id()).isEqualTo("red_flag_stroke");
        assertThat(only("My face is drooping.").id()).isEqualTo("red_flag_stroke");
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertThat(only("I CANNOT BREATHE").id()).isEqualTo("red_flag_breathing");
        assertThat(only("I Fainted and LOST CONSCIOUSNESS").id()).isEqualTo("red_flag_consciousness");
        assertThat(only("Crushing Chest Pain").id()).isEqualTo("red_flag_chest_pain");
    }

    @Test
    void noFalsePositiveFromVagueOrdinaryWords() {
        assertThat(flags("I feel a little weak and tired.")).isEmpty();
        assertThat(flags("My chest is a bit sore after sleeping badly.")).isEmpty();
        assertThat(flags("I had a small nosebleed yesterday.")).isEmpty();
        assertThat(flags("I have a headache and mild fever.")).isEmpty();
        assertThat(flags("I lost my appetite.")).isEmpty();
    }

    @Test
    void multipleMatchingRedFlagsAreReturnedInOrder() {
        List<RedFlag> result = flags("I cannot breathe and I also have crushing chest pain.");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RedFlag::id)
                .containsExactly("red_flag_breathing", "red_flag_chest_pain");
        assertThat(evaluator.overallSeverity(result)).isEqualTo(RedFlagSeverity.URGENT);
    }

    @Test
    void blankOrNullAnswerProducesNoFlags() {
        assertThat(flags(null)).isEmpty();
        assertThat(flags("  ")).isEmpty();
        assertThat(evaluator.overallSeverity(List.of())).isEqualTo(RedFlagSeverity.NONE);
    }

    @Test
    void normalizeSafelyTrimsLowercasesAndCollapsesWhitespace() {
        assertThat(evaluator.normalize("   I   Cannot BREATHE   "))
                .isEqualTo("i cannot breathe");
        assertThat(evaluator.normalize("")).isEmpty();
    }
}