package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.ai.provider.AiProviderFailure;
import in.devmedi.kiosk.module.clinical.ai.AiConversationService;
import in.devmedi.kiosk.module.clinical.ai.NextQuestionSource;
import in.devmedi.kiosk.module.clinical.ai.NextQuestionWording;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the real AiConversationService bean in a clean context (no external
 * API keys): the application must remain fully usable, every question stays
 * deterministic, and no provider is ever contacted.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiConversationFallbackIntegrationTests {

    @Autowired
    private AiConversationService aiConversationService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void withoutApiKeysNoProviderIsAvailable() {
        assertThat(aiConversationService.aiAvailable()).isFalse();
    }

    @Test
    void withoutApiKeysTheDeterministicQuestionIsReturned() {
        NextQuestionWording wording = aiConversationService.nextQuestionWording(
                "HISTORY_OF_PRESENT_ILLNESS", "ONSET", "When did it first start?",
                "A few days ago", List.of(), "en");

        assertThat(wording.source()).isEqualTo(NextQuestionSource.DETERMINISTIC_FALLBACK);
        assertThat(wording.text()).isEqualTo("When did it first start?");
        assertThat(wording.failure()).isEqualTo(AiProviderFailure.NO_PROVIDERS_AVAILABLE);
    }

    @Test
    void intakeConversationStillReturnsCanonicalQuestions() throws Exception {
        MockHttpSession session = patientSession();
        String response = mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String chief = JsonPath.read(response, "$.question.id");

        String answerResponse = mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + chief + "\",\"answer\":\"I have a headache\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(answerResponse, "$.question.id")).isEqualTo("hpi_onset");
        assertThat((String) JsonPath.read(answerResponse, "$.question.text"))
                .isEqualTo("When did it first start?");
    }

    private MockHttpSession patientSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", "patient")
                        .param("password", "patient123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}