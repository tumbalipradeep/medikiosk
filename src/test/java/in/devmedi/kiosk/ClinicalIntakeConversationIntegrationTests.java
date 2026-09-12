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
class ClinicalIntakeConversationIntegrationTests {

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

    private String postJson(String url, String body, MockHttpSession session) throws Exception {
        var request = post(url)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf());
        if (body != null) {
            request = request.content(body);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void startingTheConversationReturnsTheFirstQuestion() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.id").value("chief_complaint_symptom"))
                .andExpect(jsonPath("$.question.section").value("CHIEF_COMPLAINT"))
                .andExpect(jsonPath("$.question.type").value("SYMPTOM_PROBLEM"))
                .andExpect(jsonPath("$.question.required").value(true))
                .andExpect(jsonPath("$.question.order").value(1))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.state").value("IN_PROGRESS"));
    }

    @Test
    void submittingAnAnswerReturnsTheCorrectNextQuestion() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        String start = postJson("/patient/intake/conversation/start", null, session);
        String chief = JsonPath.read(start, "$.question.id");

        String response = postJson("/patient/intake/conversation/answer",
                "{\"questionId\":\"" + chief + "\",\"answer\":\"I have a headache.\"}", session);

        assertThat(JsonPath.<String>read(response, "$.question.id")).isEqualTo("hpi_onset");
        assertThat(JsonPath.<String>read(response, "$.question.section")).isEqualTo("HISTORY_OF_PRESENT_ILLNESS");
        assertThat(JsonPath.<String>read(response, "$.question.type")).isEqualTo("ONSET");
        assertThat(JsonPath.<Boolean>read(response, "$.completed")).isFalse();
        assertThat(JsonPath.<String>read(response, "$.state")).isEqualTo("IN_PROGRESS");
    }

    @Test
    void questionsRemainInDeterministicOrder() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        String current = JsonPath.read(postJson("/patient/intake/conversation/start", null, session), "$.question.id");
        List<String> seen = new ArrayList<>();
        seen.add(current);

        for (int i = 1; i < EXPECTED_IDS.size(); i++) {
            String response = postJson("/patient/intake/conversation/answer",
                    "{\"questionId\":\"" + current + "\",\"answer\":\"patient answer\"}", session);
            current = JsonPath.read(response, "$.question.id");
            seen.add(current);
        }

        assertThat(seen).containsExactlyElementsOf(EXPECTED_IDS);
    }

    @Test
    void completionIsReturnedOnlyAfterTheFinalAharaViharaAnswer() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        String current = JsonPath.read(postJson("/patient/intake/conversation/start", null, session), "$.question.id");
        List<String> combined = new ArrayList<>(EXPECTED_IDS);
        combined.addAll(DASHAVIDHA_IDS);
        combined.addAll(AHARA_VIHARA_IDS);
        for (int i = 1; i < combined.size(); i++) {
            String response = postJson("/patient/intake/conversation/answer",
                    "{\"questionId\":\"" + current + "\",\"answer\":\"answer\"}", session);
            current = JsonPath.read(response, "$.question.id");
        }

        mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + current + "\",\"answer\":\"last answer\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value(nullValue()))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.state").value("COMPLETED"));
    }

    @Test
    void noQuestionIsReturnedAfterCompletion() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        String current = JsonPath.read(postJson("/patient/intake/conversation/start", null, session), "$.question.id");
        List<String> combined = new ArrayList<>(EXPECTED_IDS);
        combined.addAll(DASHAVIDHA_IDS);
        combined.addAll(AHARA_VIHARA_IDS);
        for (int i = 1; i < combined.size(); i++) {
            String response = postJson("/patient/intake/conversation/answer",
                    "{\"questionId\":\"" + current + "\",\"answer\":\"a\"}", session);
            current = JsonPath.read(response, "$.question.id");
        }
        String last = current;
        postJson("/patient/intake/conversation/answer",
                "{\"questionId\":\"" + last + "\",\"answer\":\"final\"}", session);

        mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + last + "\",\"answer\":\"again\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value(nullValue()))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.state").value("COMPLETED"));
    }

    @Test
    void unauthenticatedAccessIsRejectedByExistingSecurityConfiguration() throws Exception {
        mockMvc.perform(post("/patient/intake/conversation/start")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/patient/intake"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void authenticatedPatientAccessWorks() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.id").value("chief_complaint_symptom"));
    }

    @Test
    void nonPatientRolesCannotUseTheConversationEndpoint() throws Exception {
        for (String[] creds : new String[][]{
                {"physician", "physician123"},
                {"admin", "admin123"}}) {
            MockHttpSession session = login(creds[0], creds[1]);
            mockMvc.perform(post("/patient/intake/conversation/start")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void blankQuestionIdIsRejectedWithBadRequest() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"\",\"answer\":\"x\"}")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownQuestionIdIsRejectedWithBadRequest() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"does_not_exist\",\"answer\":\"x\"}")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restartingMidConversationResumesAtTheNextQuestion() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        String chief = JsonPath.read(
                postJson("/patient/intake/conversation/start", null, session), "$.question.id");

        postJson("/patient/intake/conversation/answer",
                "{\"questionId\":\"" + chief + "\",\"answer\":\"I have a headache.\"}", session);

        String resume = postJson("/patient/intake/conversation/start", null, session);
        assertThat(JsonPath.<String>read(resume, "$.question.id")).isEqualTo("hpi_onset");
        assertThat(JsonPath.<Boolean>read(resume, "$.completed")).isFalse();
        assertThat(JsonPath.<String>read(resume, "$.state")).isEqualTo("IN_PROGRESS");
    }

    @Test
    void completionPageWithoutACompletedCaseRedirectsHome() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        mockMvc.perform(get("/patient/intake/complete")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/patient/home"));
    }

    @Test
    void aCompletedConversationIsReportedOnceWithTheSameCaseIdOnRestart() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        String current = JsonPath.read(postJson("/patient/intake/conversation/start", null, session), "$.question.id");
        List<String> combined = new ArrayList<>(EXPECTED_IDS);
        combined.addAll(DASHAVIDHA_IDS);
        combined.addAll(AHARA_VIHARA_IDS);
        for (int i = 1; i < combined.size(); i++) {
            String response = postJson("/patient/intake/conversation/answer",
                    "{\"questionId\":\"" + current + "\",\"answer\":\"answer\"}", session);
            current = JsonPath.read(response, "$.question.id");
        }
        String completed = postJson("/patient/intake/conversation/answer",
                "{\"questionId\":\"" + current + "\",\"answer\":\"final\"}", session);
        assertThat(JsonPath.<Boolean>read(completed, "$.completed")).isTrue();
        String caseId = JsonPath.read(completed, "$.caseId");

        String restarted = postJson("/patient/intake/conversation/start", null, session);
        assertThat(JsonPath.<Boolean>read(restarted, "$.completed")).isTrue();
        assertThat(JsonPath.<String>read(restarted, "$.caseId")).isEqualTo(caseId);

        String repeatedFinal = postJson("/patient/intake/conversation/answer",
                "{\"questionId\":\"" + current + "\",\"answer\":\"again\"}", session);
        assertThat(JsonPath.<Boolean>read(repeatedFinal, "$.completed")).isTrue();
        assertThat(JsonPath.<String>read(repeatedFinal, "$.caseId")).isEqualTo(caseId);
    }
}