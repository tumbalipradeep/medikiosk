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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RedFlagEngineIntegrationTests {

    private static final List<String> EXPECTED_IDS = List.of(
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

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", password)
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

    @Test
    void ordinaryAnswerProducesNoRedFlagsAndNormalProgression() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        String chief = JsonPath.read(start(session), "$.question.id");

        String response = answer(session, chief, "I have had a mild headache for two days.");

        assertThat(JsonPath.<String>read(response, "$.urgency")).isEqualTo("NONE");
        assertThat((List<?>) JsonPath.read(response, "$.redFlags")).isEmpty();
        assertThat(JsonPath.<String>read(response, "$.question.id")).isEqualTo("hpi_onset");
        assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
    }

    @Test
    void urgentAnswerReturnsRedFlagInformationAndKeepsProgressing() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        String chief = JsonPath.read(start(session), "$.question.id");

        String response = answer(session, chief, "I cannot breathe.");

        assertThat(JsonPath.<String>read(response, "$.urgency")).isEqualTo("URGENT");
        assertThat(JsonPath.<String>read(response, "$.redFlags[0].id")).isEqualTo("red_flag_breathing");
        assertThat(JsonPath.<String>read(response, "$.redFlags[0].severity")).isEqualTo("URGENT");
        assertThat(JsonPath.<String>read(response, "$.redFlags[0].title")).isNotBlank();
        assertThat(JsonPath.<String>read(response, "$.redFlags[0].message")).isNotBlank();
        assertThat(JsonPath.<String>read(response, "$.question.id")).isEqualTo("hpi_onset");
        assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
    }

    @Test
    void caseInsensitiveAndMultipleRedFlagsAreReturned() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        String chief = JsonPath.read(start(session), "$.question.id");

        String response = answer(session, chief, "I CANNOT BREATHE and I have CRUSHING CHEST PAIN.");

        assertThat((List<?>) JsonPath.read(response, "$.redFlags")).hasSize(2);
        assertThat(JsonPath.<String>read(response, "$.redFlags[0].id")).isEqualTo("red_flag_breathing");
        assertThat(JsonPath.<String>read(response, "$.redFlags[1].id")).isEqualTo("red_flag_chest_pain");
        assertThat(JsonPath.<String>read(response, "$.urgency")).isEqualTo("URGENT");
    }

    private String walkAcrossAllSequences(MockHttpSession session, String initial, String answerText) throws Exception {
        String current = initial;
        String lastResponse = null;
        List<String> combined = new ArrayList<>(EXPECTED_IDS);
        combined.addAll(DASHAVIDHA_IDS);
        combined.addAll(AHARA_VIHARA_IDS);
        for (int i = 1; i < combined.size(); i++) {
            String response = answer(session, current, answerText);
            assertThat(JsonPath.<String>read(response, "$.urgency")).isEqualTo("NONE");
            current = JsonPath.read(response, "$.question.id");
            lastResponse = response;
        }
        return lastResponse;
    }

    @Test
    void normalQuestionProgressionCompletesWithoutRedFlagsAcrossAllSequences() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        String current = JsonPath.read(start(session), "$.question.id");
        String lastResponse = walkAcrossAllSequences(session, current, "a normal answer");
        assertThat(JsonPath.<String>read(lastResponse, "$.question.id")).isEqualTo("ahara_vihara_habits");

        String finalResponse = answer(session, "ahara_vihara_habits", "all done");

        assertThat(JsonPath.<Boolean>read(finalResponse, "$.completed")).isTrue();
        assertThat(JsonPath.<String>read(finalResponse, "$.urgency")).isEqualTo("NONE");
        assertThat(JsonPath.<String>read(finalResponse, "$.state")).isEqualTo("COMPLETED");
        assertThat((List<?>) JsonPath.read(finalResponse, "$.redFlags")).isEmpty();
        assertThat(JsonPath.<Object>read(finalResponse, "$.question")).isNull();
    }

    @Test
    void urgentFlagOnFinalHpiAnswerTransitionsToDashavidhaWithUrgency() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        String current = JsonPath.read(start(session), "$.question.id");
        for (int i = 1; i < EXPECTED_IDS.size(); i++) {
            String response = answer(session, current, "normal " + i);
            current = JsonPath.read(response, "$.question.id");
        }
        String last = current;
        String response = answer(session, last, "I fainted again");

        assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
        assertThat(JsonPath.<String>read(response, "$.urgency")).isEqualTo("URGENT");
        assertThat(JsonPath.<String>read(response, "$.redFlags[0].id")).isEqualTo("red_flag_consciousness");
        assertThat(JsonPath.<String>read(response, "$.question.section")).isEqualTo("DASHAVIDHA");
        assertThat(JsonPath.<String>read(response, "$.question.id")).isEqualTo("dashavidha_prakriti");
    }

    @Test
    void urgentFlagStillWorksDuringTheDashavidhaSequence() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        String response = answer(session, "dashavidha_prakriti", "I cannot breathe and my left arm hurts.");

        assertThat(JsonPath.<String>read(response, "$.urgency")).isEqualTo("URGENT");
        assertThat(JsonPath.<String>read(response, "$.redFlags[0].id")).isEqualTo("red_flag_breathing");
        assertThat(JsonPath.<String>read(response, "$.question.section")).isEqualTo("DASHAVIDHA");
        assertThat(JsonPath.<String>read(response, "$.question.id")).isEqualTo("dashavidha_vikriti");
        assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
    }

    @Test
    void patientSecurityAndAccessRemainUnchanged() throws Exception {
        mockMvc.perform(post("/patient/intake/conversation/start")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/patient/intake"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        MockHttpSession patient = login("patient", "patient123");
        mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(patient)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.id").value("chief_complaint_symptom"));

        MockHttpSession physician = login("physician", "physician123");
        mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(physician)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}