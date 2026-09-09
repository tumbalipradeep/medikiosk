package in.devmedi.kiosk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.allOf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PatientIntakeConversationIntegrationTests {

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

    @Test
    void unauthenticatedUsersAreRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/patient/intake"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getRedirectedUrl())
                        .isEqualTo("/login"));
    }

    @Test
    void nonPatientRolesCannotViewIntake() throws Exception {
        for (String[] creds : new String[][]{
                {"physician", "physician123"},
                {"admin", "admin123"}}) {
            MockHttpSession session = login(creds[0], creds[1]);
            mockMvc.perform(get("/patient/intake").session(session))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void intakeConversationControlsRenderForAuthenticatedPatient() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        mockMvc.perform(get("/patient/intake").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Patient Intake Conversation"),
                        containsString("id=\"chatWindow\""),
                        containsString("id=\"chatMessages\""),
                        containsString("chat-message assistant"),
                        containsString("chat-message patient"),
                        containsString("id=\"assistantGreeting\""),
                        containsString("id=\"messageInput\""),
                        containsString("id=\"sendButton\""),
                        containsString("id=\"micButton\""),
                        containsString("id=\"speakQuestion\"")
                )));
    }

    @Test
    void languageSelectorListsAllSupportedLanguages() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        String body = mockMvc.perform(get("/patient/intake").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String lang : new String[]{"English", "Hindi", "Telugu", "Tamil", "Kannada"}) {
            org.assertj.core.api.Assertions.assertThat(body).contains(lang);
        }
    }

    @Test
    void currentLanguageBadgeIsVisibleAndDefaultsToEnglish() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        mockMvc.perform(get("/patient/intake").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"currentLangBadge\""),
                        containsString(">English</span>")
                )));
    }

    @Test
    void conversationStaticAssetsAreAccessible() throws Exception {
        mockMvc.perform(get("/js/intake.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("chat-message")));
    }
}