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
 * Focused CP4-E.2 tests covering the combined HPI/SOCRATES then Dashavidha
 * conversation flow and its unchanged security boundaries.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CombinedConversationIntegrationTests {

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

    private static final List<String> DASHAVIDHA_PARAMETERS = List.of(
            "PRAKRITI", "VIKRITI", "SARA", "SAMHANANA", "PRAMANA",
            "SATMYA", "SATTVA", "AHARA_SHAKTI", "VYAYAMA_SHAKTI", "VAYA");

    private static final List<String> AHARA_VIHARA_IDS = List.of(
            "ahara_vihara_ahara",
            "ahara_vihara_meal_pattern",
            "ahara_vihara_appetite",
            "ahara_vihara_hydration",
            "ahara_vihara_sleep",
            "ahara_vihara_physical_activity",
            "ahara_vihara_daily_routine",
            "ahara_vihara_habits");

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

    /** Answers each question id in order, for example all seven HPI questions, returning the final response. */
    private String answerIds(MockHttpSession session, List<String> ids, String answerText) throws Exception {
        String response = null;
        for (String id : ids) {
            response = answer(session, id, answerText);
        }
        return response;
    }

    /** Walks the whole HPI sequence and returns the response after the last HPI answer (the transition). */
    private String walkThroughHpi(MockHttpSession session) throws Exception {
        return answerIds(session, HPI_IDS, ORDINARY);
    }

    @Test
    void hpiFlowStillStartsCorrectly() throws Exception {
        MockHttpSession session = patientSession();

        String response = start(session);

        assertThat(JsonPath.<String>read(response, "$.question.id")).isEqualTo("chief_complaint_symptom");
        assertThat(JsonPath.<String>read(response, "$.question.section")).isEqualTo("CHIEF_COMPLAINT");
        assertThat(JsonPath.<String>read(response, "$.state")).isEqualTo("IN_PROGRESS");
        assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
    }

    @Test
    void hpiOrderingRemainsUnchanged() throws Exception {
        MockHttpSession session = patientSession();
        String current = JsonPath.read(start(session), "$.question.id");

        for (int i = 1; i < HPI_IDS.size(); i++) {
            String response = answer(session, current, ORDINARY);
            current = JsonPath.read(response, "$.question.id");
            assertThat(current).isEqualTo(HPI_IDS.get(i));
            assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
        }
    }

    @Test
    void finalHpiAnswerTransitionsToFirstDashavidhaQuestion() throws Exception {
        MockHttpSession session = patientSession();

        String response = walkThroughHpi(session);

        assertThat(JsonPath.<String>read(response, "$.question.id")).isEqualTo("dashavidha_prakriti");
        assertThat(JsonPath.<String>read(response, "$.question.section")).isEqualTo("DASHAVIDHA");
        assertThat(JsonPath.<String>read(response, "$.question.type")).isEqualTo("PRAKRITI");
        assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
        assertThat(JsonPath.<String>read(response, "$.state")).isEqualTo("IN_PROGRESS");
    }

    @Test
    void dashavidhaQuestionsProgressInOrder() throws Exception {
        MockHttpSession session = patientSession();
        String current = JsonPath.read(walkThroughHpi(session), "$.question.id");
        assertThat(current).isEqualTo("dashavidha_prakriti");

        for (int i = 1; i < DASHAVIDHA_IDS.size(); i++) {
            String response = answer(session, current, ORDINARY);
            current = JsonPath.read(response, "$.question.id");
            assertThat(current).isEqualTo(DASHAVIDHA_IDS.get(i));
            assertThat(JsonPath.<String>read(response, "$.question.section")).isEqualTo("DASHAVIDHA");
        }
    }

    @Test
    void allTenDashavidhaParametersAreReached() throws Exception {
        MockHttpSession session = patientSession();
        String current = JsonPath.read(walkThroughHpi(session), "$.question.id");

        for (int i = 1; i < DASHAVIDHA_IDS.size(); i++) {
            String response = answer(session, current, ORDINARY);
            current = JsonPath.read(response, "$.question.id");
            assertThat(JsonPath.<String>read(response, "$.question.type")).isEqualTo(DASHAVIDHA_PARAMETERS.get(i));
            assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
        }
        assertThat(current).isEqualTo("dashavidha_vaya");
    }

    @Test
    void completionComesAfterTheFinalAharaViharaAnswer() throws Exception {
        MockHttpSession session = patientSession();
        String current = JsonPath.read(walkThroughHpi(session), "$.question.id");
        assertThat(current).isEqualTo("dashavidha_prakriti");
        for (int i = 1; i < DASHAVIDHA_IDS.size(); i++) {
            String response = answer(session, current, ORDINARY);
            current = JsonPath.read(response, "$.question.id");
        }
        assertThat(current).isEqualTo("dashavidha_vaya");
        String transition = answer(session, current, ORDINARY);
        assertThat(JsonPath.<String>read(transition, "$.question.section")).isEqualTo("AHARA_VIHARA");
        assertThat(JsonPath.<String>read(transition, "$.question.id")).isEqualTo("ahara_vihara_ahara");
        current = JsonPath.read(transition, "$.question.id");

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
        String current = JsonPath.read(walkThroughHpi(session), "$.question.id");
        for (int i = 1; i < DASHAVIDHA_IDS.size(); i++) {
            String response = answer(session, current, ORDINARY);
            current = JsonPath.read(response, "$.question.id");
        }
        String transition = answer(session, current, ORDINARY);
        current = JsonPath.read(transition, "$.question.id");
        assertThat(current).isEqualTo("ahara_vihara_ahara");
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
    void redFlagsStillWorkDuringTheCombinedFlowForHpiAndDashavidha() throws Exception {
        MockHttpSession session = patientSession();
        String current = JsonPath.read(start(session), "$.question.id");

        String response = answer(session, current, "I cannot breathe.");
        assertThat(JsonPath.<String>read(response, "$.urgency")).isEqualTo("URGENT");
        assertThat(JsonPath.<String>read(response, "$.redFlags[0].id")).isEqualTo("red_flag_breathing");
        assertThat(JsonPath.<String>read(response, "$.question.section")).isEqualTo("HISTORY_OF_PRESENT_ILLNESS");
        assertThat(JsonPath.<String>read(response, "$.question.id")).isEqualTo("hpi_onset");

        String transition = answerIds(session, HPI_IDS.subList(1, HPI_IDS.size()), ORDINARY);
        assertThat(JsonPath.<String>read(transition, "$.question.section")).isEqualTo("DASHAVIDHA");
        assertThat(JsonPath.<String>read(transition, "$.question.id")).isEqualTo("dashavidha_prakriti");

        String dashavidhaResponse = answer(session, "dashavidha_prakriti",
                "I felt the crushing chest pain again");
        assertThat(JsonPath.<String>read(dashavidhaResponse, "$.urgency")).isEqualTo("URGENT");
        assertThat(JsonPath.<String>read(dashavidhaResponse, "$.redFlags[0].id")).isEqualTo("red_flag_chest_pain");
        assertThat(JsonPath.<String>read(dashavidhaResponse, "$.question.section")).isEqualTo("DASHAVIDHA");
        assertThat(JsonPath.<String>read(dashavidhaResponse, "$.question.id")).isEqualTo("dashavidha_vikriti");
    }

    @Test
    void patientAccessAndSecurityRemainUnchanged() throws Exception {
        mockMvc.perform(post("/patient/intake/conversation/start")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        MockHttpSession patient = patientSession();
        mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(patient)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.id").value("chief_complaint_symptom"));

        MvcResult physicianResult = mockMvc.perform(post("/login")
                        .param("username", "physician")
                        .param("password", "physician123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession physician =
                (MockHttpSession) physicianResult.getRequest().getSession(false);
        mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(physician)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}