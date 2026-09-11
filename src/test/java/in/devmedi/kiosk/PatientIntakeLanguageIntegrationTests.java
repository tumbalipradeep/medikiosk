package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.clinical.controller.ClinicalIntakeConversationController;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the patient-facing language selection foundation: the fixed
 * supported set, English as the deterministic default, session-scoped
 * persistence, safe rejection of unsupported codes, and full independence from
 * clinical progression.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PatientIntakeLanguageIntegrationTests {

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

    private MockHttpSession patientSession() throws Exception {
        return login("patient", "patient123");
    }

    @Test
    void defaultLanguageIsEnglishWhenNothingIsSelected() throws Exception {
        MockHttpSession session = patientSession();

        String body = mockMvc.perform(get("/patient/intake/language").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("en-IN");
        assertThat((String) JsonPath.read(body, "$.label")).isEqualTo("English");
        assertThat((String) JsonPath.read(body, "$.bcp47")).isEqualTo("en-IN");
    }

    @Test
    void languagesEndpointListsExactlyTheFiveSupportedLanguages() throws Exception {
        MockHttpSession session = patientSession();

        String body = mockMvc.perform(get("/patient/intake/languages").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((Integer) JsonPath.read(body, "$.length()")).isEqualTo(5);
        assertThat((String) JsonPath.read(body, "$[0].code")).isEqualTo("en-IN");
        assertThat(JsonPath.read(body, "$[*].code").toString())
                .contains("en-IN", "hi-IN", "te-IN", "ta-IN", "kn-IN")
                .doesNotContain("xx");
    }

    @Test
    void selectionPersistsForTheCurrentIntakeSession() throws Exception {
        MockHttpSession session = patientSession();

        mockMvc.perform(post("/patient/intake/language")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"te-IN\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/patient/intake/language").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("te-IN");
        assertThat((String) JsonPath.read(body, "$.label")).isEqualTo("Telugu");
        assertThat((String) JsonPath.read(body, "$.bcp47")).isEqualTo("te-IN");
    }

    @Test
    void unsupportedLanguageIsRejectedAndCurrentSelectionIsUnchanged() throws Exception {
        MockHttpSession session = patientSession();

        mockMvc.perform(post("/patient/intake/language")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"xx\"}")
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        String body = mockMvc.perform(get("/patient/intake/language").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("en-IN");
    }

    @Test
    void blankSelectionResolvesToEnglishDefault() throws Exception {
        MockHttpSession session = patientSession();

        mockMvc.perform(post("/patient/intake/language")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/patient/intake/language").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("en-IN");
    }

    @Test
    void languageSwitchDoesNotResetOrAlterTheClinicalConversation() throws Exception {
        MockHttpSession session = patientSession();

        String startResponse = mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String chief = JsonPath.read(startResponse, "$.question.id");

        mockMvc.perform(post("/patient/intake/language")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"hi-IN\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        String answerResponse = mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + chief + "\",\"answer\":\"My stomach hurts\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(answerResponse, "$.question.id")).isEqualTo("hpi_onset");
        assertThat((String) JsonPath.read(answerResponse, "$.question.section"))
                .isEqualTo("HISTORY_OF_PRESENT_ILLNESS");

        ClinicalConversationResult result = (ClinicalConversationResult) session.getAttribute(
                ClinicalIntakeConversationController.RESULT_ATTRIBUTE);
        assertThat(result.size()).isEqualTo(1);

        String persisted = mockMvc.perform(get("/patient/intake/language").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(persisted, "$.code")).isEqualTo("hi-IN");
    }

    @Test
    void aFreshSessionAlwaysDefaultsToEnglishWithoutAnotherPatientsSelection() throws Exception {
        MockHttpSession firstSession = patientSession();
        mockMvc.perform(post("/patient/intake/language")
                        .session(firstSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"kn-IN\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        MockHttpSession secondSession = patientSession();
        String body = mockMvc.perform(get("/patient/intake/language").session(secondSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("en-IN");
    }

    @Test
    void unauthenticatedRequestsAreRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/patient/intake/language"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/patient/intake/language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"hi-IN\"}")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void nonPatientRolesCannotReadOrChangeLanguage() throws Exception {
        MockHttpSession physician = login("physician", "physician123");
        MockHttpSession admin = login("admin", "admin123");

        for (MockHttpSession session : new MockHttpSession[]{physician, admin}) {
            mockMvc.perform(get("/patient/intake/language").session(session))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/patient/intake/language")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"language\":\"hi-IN\"}")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void intakePageRendersTheCurrentlySelectedLanguageBadge() throws Exception {
        MockHttpSession session = patientSession();
        mockMvc.perform(post("/patient/intake/language")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"ta-IN\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        String page = mockMvc.perform(get("/patient/intake").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains(">Tamil</span>");
        assertThat(page).contains("data-lang=\"ta-IN\"");
        assertThat(page).contains("value=\"en-IN\"").contains("value=\"hi-IN\"")
                .contains("value=\"te-IN\"").contains("value=\"ta-IN\"").contains("value=\"kn-IN\"");
    }
}