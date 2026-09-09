package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Focused CP4-E.3 tests covering the Ahara-Vihara segment as part of the full
 * HPI → Dashavidha → Ahara-Vihara → completed conversation flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AharaViharaConversationIntegrationTests {

    private static final List<String> HPI_IDS = List.of(
            "chief_complaint_symptom",
            "hpi_onset",
            "hpi_provocation_palliation",
            "hpi_quality",
            "hpi_region_radiation",
            "hpi_severity",
            "hpi_timing_duration");

    private static final List<String> DASHAVIDHA_IDS = List.of(
            "dashavidha_prakriti",
            "dashavidha_vikriti",
            "dashavidha_sara",
            "dashavidha_samhanana",
            "dashavidha_pramana",
            "dashavidha_satmya",
            "dashavidha_sattva",
            "dashavidha_ahara_shakti",
            "dashavidha_vyayama_shakti",
            "dashavidha_vaya");

    private static final List<String> AHARA_VIHARA_IDS = List.of(
            "ahara_vihara_ahara",
            "ahara_vihara_meal_pattern",
            "ahara_vihara_appetite",
            "ahara_vihara_hydration",
            "ahara_vihara_sleep",
            "ahara_vihara_physical_activity",
            "ahara_vihara_daily_routine",
            "ahara_vihara_habits");

    private static final List<String> AHARA_VIHARA_PARAMETERS = List.of(
            "AHARA", "MEAL_PATTERN", "APPETITE", "HYDRATION",
            "SLEEP", "PHYSICAL_ACTIVITY", "DAILY_ROUTINE", "HABITS");

    private static final String ORDINARY = "a normal patient description";

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

    /** Answers every question id in order and returns the final response string. */
    private String answerIds(MockHttpSession session, List<String> ids, String answerText) throws Exception {
        String response = null;
        for (String id : ids) {
            response = answer(session, id, answerText);
        }
        return response;
    }

    /** Walks HPI then Dashavidha and returns the response after the tenth Dashavidha answer (the transition). */
    private String walkThroughDashavidha(MockHttpSession session) throws Exception {
        answerIds(session, HPI_IDS, ORDINARY);
        return answerIds(session, DASHAVIDHA_IDS, ORDINARY);
    }

    @Test
    void finalDashavidhaAnswerTransitionsToFirstAharaViharaQuestion() throws Exception {
        MockHttpSession session = patientSession();

        String response = walkThroughDashavidha(session);

        assertThat(JsonPath.<String>read(response, "$.question.id")).isEqualTo("ahara_vihara_ahara");
        assertThat(JsonPath.<String>read(response, "$.question.section")).isEqualTo("AHARA_VIHARA");
        assertThat(JsonPath.<String>read(response, "$.question.type")).isEqualTo("AHARA");
        assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
        assertThat(JsonPath.<String>read(response, "$.state")).isEqualTo("IN_PROGRESS");
    }

    @Test
    void aharaViharaQuestionsProgressInOrder() throws Exception {
        MockHttpSession session = patientSession();
        String current = JsonPath.read(walkThroughDashavidha(session), "$.question.id");
        assertThat(current).isEqualTo("ahara_vihara_ahara");

        for (int i = 1; i < AHARA_VIHARA_IDS.size(); i++) {
            String response = answer(session, current, ORDINARY);
            current = JsonPath.read(response, "$.question.id");
            assertThat(current).isEqualTo(AHARA_VIHARA_IDS.get(i));
            assertThat(JsonPath.<String>read(response, "$.question.section")).isEqualTo("AHARA_VIHARA");
        }
    }

    @Test
    void allEightAharaViharaParametersAreReached() throws Exception {
        MockHttpSession session = patientSession();
        String current = JsonPath.read(walkThroughDashavidha(session), "$.question.id");

        for (int i = 1; i < AHARA_VIHARA_IDS.size(); i++) {
            String response = answer(session, current, ORDINARY);
            current = JsonPath.read(response, "$.question.id");
            assertThat(JsonPath.<String>read(response, "$.question.type")).isEqualTo(AHARA_VIHARA_PARAMETERS.get(i));
            assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
        }
        assertThat(current).isEqualTo("ahara_vihara_habits");
    }

    @Test
    void completionComesAfterTheFinalAharaViharaAnswer() throws Exception {
        MockHttpSession session = patientSession();
        String current = JsonPath.read(walkThroughDashavidha(session), "$.question.id");
        assertThat(current).isEqualTo("ahara_vihara_ahara");

        for (int i = 0; i < AHARA_VIHARA_IDS.size(); i++) {
            String response = answer(session, current, ORDINARY);
            if (i < AHARA_VIHARA_IDS.size() - 1) {
                current = JsonPath.read(response, "$.question.id");
                assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
            } else {
                assertThat(JsonPath.<Boolean>read(response, "$.completed")).isTrue();
                assertThat(JsonPath.<String>read(response, "$.state")).isEqualTo("COMPLETED");
                assertThat(JsonPath.<Object>read(response, "$.question")).isNull();
            }
        }
    }

    @Test
    void noQuestionIsReturnedAfterTheWholeFlowCompletes() throws Exception {
        MockHttpSession session = patientSession();
        String current = JsonPath.read(walkThroughDashavidha(session), "$.question.id");
        for (int i = 0; i < AHARA_VIHARA_IDS.size() - 1; i++) {
            String response = answer(session, current, ORDINARY);
            current = JsonPath.read(response, "$.question.id");
        }
        assertThat(current).isEqualTo("ahara_vihara_habits");

        mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + current + "\",\"answer\":\"final\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value(nullValue()))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.state").value("COMPLETED"));

        mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + current + "\",\"answer\":\"again\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value(nullValue()))
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void redFlagsStillWorkDuringAharaViharaAnswers() throws Exception {
        MockHttpSession session = patientSession();
        walkThroughDashavidha(session);

        String response = answer(session, "ahara_vihara_ahara",
                "I cannot breathe at night and I lost consciousness once");

        assertThat(JsonPath.<String>read(response, "$.urgency")).isEqualTo("URGENT");
        assertThat(JsonPath.<String>read(response, "$.redFlags[0].id")).isEqualTo("red_flag_breathing");
        assertThat(JsonPath.<String>read(response, "$.redFlags[1].id")).isEqualTo("red_flag_consciousness");
        assertThat(JsonPath.<String>read(response, "$.question.section")).isEqualTo("AHARA_VIHARA");
        assertThat(JsonPath.<String>read(response, "$.question.id")).isEqualTo("ahara_vihara_meal_pattern");
        assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
    }

    @Test
    void dashavidhaSequenceIsUnchangedWhenFollowedByAharaVihara() throws Exception {
        MockHttpSession session = patientSession();
        answerIds(session, HPI_IDS, ORDINARY);

        String transitionResponse = answerIds(session, DASHAVIDHA_IDS, ORDINARY);
        assertThat(JsonPath.<String>read(transitionResponse, "$.question.section")).isEqualTo("AHARA_VIHARA");
        assertThat(JsonPath.<String>read(transitionResponse, "$.question.id")).isEqualTo("ahara_vihara_ahara");

        MockHttpSession session2 = patientSession();
        String current = JsonPath.read(start(session2), "$.question.id");
        assertThat(current).isEqualTo("chief_complaint_symptom");
        for (int i = 1; i < HPI_IDS.size(); i++) {
            String response = answer(session2, current, ORDINARY);
            current = JsonPath.read(response, "$.question.id");
            assertThat(current).isEqualTo(HPI_IDS.get(i));
        }
    }
}