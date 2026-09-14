package in.devmedi.kiosk;

import in.devmedi.kiosk.module.clinical.adaptive.AdaptiveConversationPlanner;
import in.devmedi.kiosk.module.clinical.adaptive.AdaptiveHistoryQuestion;
import in.devmedi.kiosk.module.clinical.adaptive.CompleteHistoryQuestionBank;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveConversationPlannerTests {

    private final CompleteHistoryQuestionBank bank = new CompleteHistoryQuestionBank();
    private final AdaptiveConversationPlanner planner = new AdaptiveConversationPlanner();

    @Test
    void firstQuestionIsThePastMedicalHistoryScreener() {
        Optional<AdaptiveHistoryQuestion> first = planner.next(bank, Map.of());

        assertThat(first).isPresent();
        assertThat(first.get().id()).isEqualTo("pmh_chronic_illness");
        assertThat(first.get().category().name()).isEqualTo("PAST_MEDICAL");
    }

    @Test
    void negativeScreeningGatesDetailQuestions() {
        Map<String, String> answers = Map.of("pmh_chronic_illness", "No, I have no chronic illness",
                "psh_any_surgery", "No");

        List<AdaptiveHistoryQuestion> applicable = planner.applicable(bank, answers);

        assertThat(applicable.stream().map(AdaptiveHistoryQuestion::id))
                .doesNotContain("pmh_diabetes", "pmh_hypertension", "psh_surgery_detail");
        assertThat(applicable.stream().map(AdaptiveHistoryQuestion::id))
                .contains("med_current", "allg_any");
    }

    @Test
    void affirmativeScreeningActivatedDetailQuestions() {
        Map<String, String> answers = Map.of("pmh_chronic_illness", "Yes, I have type 2 diabetes",
                "med_current", "I am taking metformin");

        List<AdaptiveHistoryQuestion> applicable = planner.applicable(bank, answers);

        assertThat(applicable.stream().map(AdaptiveHistoryQuestion::id))
                .contains("pmh_diabetes", "med_list");
        assertThat(applicable.stream().map(AdaptiveHistoryQuestion::id))
                .doesNotContain("psh_surgery_detail");
    }

    @Test
    void keywordTriggerMakesQuestionApplicableAcrossCategories() {
        Map<String, String> answers = Map.of("pmh_chronic_illness", "No",
                "med_current", "I take insulin twice a day");

        List<AdaptiveHistoryQuestion> applicable = planner.applicable(bank, answers);

        assertThat(applicable.stream().map(AdaptiveHistoryQuestion::id)).contains("med_injections");
    }

    @Test
    void priorityOrderingWhenManyQuestionsArePending() {
        Optional<AdaptiveHistoryQuestion> first = planner.next(bank, Map.of());
        assertThat(first).isPresent();
        assertThat(first.get().id()).isEqualTo("pmh_chronic_illness");

        Optional<AdaptiveHistoryQuestion> afterPmh = planner.next(bank,
                Map.of("pmh_chronic_illness", "No"));
        assertThat(afterPmh).isPresent();
        assertThat(afterPmh.get().id()).isEqualTo("pmh_other_conditions");

        Optional<AdaptiveHistoryQuestion> afterOther = planner.next(bank,
                Map.of("pmh_chronic_illness", "No", "pmh_other_conditions", "Nothing else"));
        assertThat(afterOther).isPresent();
        assertThat(afterOther.get().id()).isEqualTo("psh_any_surgery");
    }

    @Test
    void conversationCompletesWhenAllApplicableAreAnswered() {
        Map<String, String> all = new java.util.LinkedHashMap<>();
        for (AdaptiveHistoryQuestion q : bank.questions()) {
            all.put(q.id(), "No, nothing of that sort");
        }

        assertThat(planner.next(bank, all)).isEmpty();
    }

    @Test
    void explicitNegativeDetection() {
        assertThat(AdaptiveConversationPlanner.isExplicitNegative("No")).isTrue();
        assertThat(AdaptiveConversationPlanner.isExplicitNegative("none")).isTrue();
        assertThat(AdaptiveConversationPlanner.isExplicitNegative("No, I do not have that")).isTrue();
        assertThat(AdaptiveConversationPlanner.isExplicitNegative("I usually take metformin")).isFalse();
        assertThat(AdaptiveConversationPlanner.isExplicitNegative("sometimes")).isFalse();
    }
}