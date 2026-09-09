package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.clinical.controller.ClinicalIntakeConversationController;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP4-F.1 tests: proving every submitted intake answer is captured, in order,
 * into the in-memory session-scoped {@link ClinicalConversationResult} while
 * the existing HPI → Dashavidha → Ahara-Vihara progression and red-flag
 * evaluation remain unchanged.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClinicalConversationResultIntegrationTests {

    private static final List<String> ALL_IDS = List.of(
            "chief_complaint_symptom",
            "hpi_onset",
            "hpi_provocation_palliation",
            "hpi_quality",
            "hpi_region_radiation",
            "hpi_severity",
            "hpi_timing_duration",
            "dashavidha_prakriti",
            "dashavidha_vikriti",
            "dashavidha_sara",
            "dashavidha_samhanana",
            "dashavidha_pramana",
            "dashavidha_satmya",
            "dashavidha_sattva",
            "dashavidha_ahara_shakti",
            "dashavidha_vyayama_shakti",
            "dashavidha_vaya",
            "ahara_vihara_ahara",
            "ahara_vihara_meal_pattern",
            "ahara_vihara_appetite",
            "ahara_vihara_hydration",
            "ahara_vihara_sleep",
            "ahara_vihara_physical_activity",
            "ahara_vihara_daily_routine",
            "ahara_vihara_habits");

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession patientSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", "patient")
                        .param("password", "patient123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String start(MockHttpSession session) throws Exception {
        return mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String answer(MockHttpSession session, String questionId, String answerText) throws Exception {
        return mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\",\"answer\":\""
                                + answerText.replace("\"", "\\\"") + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ClinicalConversationResult resultOf(MockHttpSession session) {
        return (ClinicalConversationResult) session.getAttribute(
                ClinicalIntakeConversationController.RESULT_ATTRIBUTE);
    }

    @Test
    void aSingleAnswerIsCapturedCorrectly() throws Exception {
        MockHttpSession session = patientSession();
        String chief = JsonPath.read(start(session), "$.question.id");

        String response = answer(session, chief, "I have a splitting headache.");

        ClinicalConversationResult result = resultOf(session);
        assertThat(result.size()).isEqualTo(1);
        ClinicalAnswer captured = result.all().get(0);
        assertThat(captured.questionId()).isEqualTo("chief_complaint_symptom");
        assertThat(captured.section()).isEqualTo("CHIEF_COMPLAINT");
        assertThat(captured.questionType()).isEqualTo("SYMPTOM_PROBLEM");
        assertThat(captured.questionText())
                .isEqualTo("What is the main problem or symptom that brought you here today?");
        assertThat(captured.answer()).isEqualTo("I have a splitting headache.");
        // progression still works after capture
        assertThat(JsonPath.<String>read(response, "$.question.id")).isEqualTo("hpi_onset");
    }

    @Test
    void multipleAnswersRemainInTheCorrectOrder() throws Exception {
        MockHttpSession session = patientSession();
        String current = JsonPath.read(start(session), "$.question.id");
        List<String> wanted = List.of("chief_complaint_symptom", "hpi_onset", "hpi_provocation_palliation");
        List<String> texts = List.of("first answer", "second answer", "third answer");

        for (int i = 0; i < wanted.size(); i++) {
            assertThat(current).isEqualTo(wanted.get(i));
            String response = answer(session, current, texts.get(i));
            current = JsonPath.read(response, "$.question.id");
        }

        ClinicalConversationResult result = resultOf(session);
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.all()).extracting(ClinicalAnswer::questionId).containsExactlyElementsOf(wanted);
        assertThat(result.all()).extracting(ClinicalAnswer::answer).containsExactlyElementsOf(texts);
        assertThat(result.all()).extracting(ClinicalAnswer::questionText)
                .containsExactly(
                        "What is the main problem or symptom that brought you here today?",
                        "When did it first start?",
                        "What makes it better or worse?");
    }

    @Test
    void full25QuestionJourneyIsCapturedInOrderAcrossAllSections() throws Exception {
        MockHttpSession session = patientSession();
        String current = JsonPath.read(start(session), "$.question.id");

        List<String> visited = new ArrayList<>();
        List<String> answersGiven = new ArrayList<>();
        for (int i = 0; i < ALL_IDS.size() - 1; i++) {
            String text = "text-" + current;
            String response = answer(session, current, text);
            visited.add(current);
            answersGiven.add(text);
            current = JsonPath.read(response, "$.question.id");
        }
        String last = "text-" + current;
        String finalResponse = answer(session, current, last);
        visited.add(current);
        answersGiven.add(last);

        // conversation completed only after all 25
        assertThat(JsonPath.<Boolean>read(finalResponse, "$.completed")).isTrue();
        assertThat(visited).containsExactlyElementsOf(ALL_IDS);

        ClinicalConversationResult result = resultOf(session);
        assertThat(result.size()).isEqualTo(25);
        List<ClinicalAnswer> all = result.all();

        assertThat(all).extracting(ClinicalAnswer::questionId).containsExactlyElementsOf(ALL_IDS);
        assertThat(all).extracting(ClinicalAnswer::answer).containsExactlyElementsOf(answersGiven);
        assertThat(all).extracting(ClinicalAnswer::section).containsSubsequence(
                List.of("CHIEF_COMPLAINT", "HISTORY_OF_PRESENT_ILLNESS", "HISTORY_OF_PRESENT_ILLNESS",
                        "HISTORY_OF_PRESENT_ILLNESS", "HISTORY_OF_PRESENT_ILLNESS", "HISTORY_OF_PRESENT_ILLNESS",
                        "HISTORY_OF_PRESENT_ILLNESS"));
        assertThat(all).extracting(ClinicalAnswer::section).containsSubsequence(
                List.of("DASHAVIDHA", "DASHAVIDHA", "DASHAVIDHA", "DASHAVIDHA", "DASHAVIDHA",
                        "DASHAVIDHA", "DASHAVIDHA", "DASHAVIDHA", "DASHAVIDHA", "DASHAVIDHA"));
        assertThat(all).extracting(ClinicalAnswer::section).containsSubsequence(
                List.of("AHARA_VIHARA", "AHARA_VIHARA", "AHARA_VIHARA", "AHARA_VIHARA",
                        "AHARA_VIHARA", "AHARA_VIHARA", "AHARA_VIHARA", "AHARA_VIHARA"));
        assertThat(all.get(0).questionType()).isEqualTo("SYMPTOM_PROBLEM");
        assertThat(all.get(0).section()).isEqualTo("CHIEF_COMPLAINT");
        assertThat(all.get(7).questionType()).isEqualTo("PRAKRITI");
        assertThat(all.get(7).section()).isEqualTo("DASHAVIDHA");
        assertThat(all.get(17).questionType()).isEqualTo("AHARA");
        assertThat(all.get(17).section()).isEqualTo("AHARA_VIHARA");
        assertThat(all.get(24).questionType()).isEqualTo("HABITS");
    }

    @Test
    void redFlagsAreStillEvaluatedWhileTheAnswerIsCaptured() throws Exception {
        MockHttpSession session = patientSession();
        String chief = JsonPath.read(start(session), "$.question.id");

        String response = answer(session, chief, "I cannot breathe.");

        assertThat(JsonPath.<String>read(response, "$.urgency")).isEqualTo("URGENT");
        assertThat(JsonPath.<String>read(response, "$.redFlags[0].id")).isEqualTo("red_flag_breathing");
        assertThat(JsonPath.<String>read(response, "$.question.id")).isEqualTo("hpi_onset");

        ClinicalConversationResult result = resultOf(session);
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.all().get(0).answer()).isEqualTo("I cannot breathe.");
    }

    @Test
    void aFreshStartResetsTheCapturedConversation() throws Exception {
        MockHttpSession session = patientSession();
        String chief = JsonPath.read(start(session), "$.question.id");
        answer(session, chief, "first conversation");

        ClinicalConversationResult afterFirst = resultOf(session);
        assertThat(afterFirst.size()).isEqualTo(1);

        start(session);
        ClinicalConversationResult afterReset = resultOf(session);
        assertThat(afterReset).isNotSameAs(afterFirst);
        assertThat(afterReset.isEmpty()).isTrue();
    }
}