package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.clinical.dialogue.AnswerSource;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that voice-originated answers enter the exact same clinical answer
 * pipeline as typed answers: the transcribed text is recorded verbatim, red
 * flags evaluate the raw answer, and the answer source metadata (TEXT/VOICE)
 * never changes question identity, progression, or completion.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClinicalVoiceAnswerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession loginPatient() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", "patient")
                        .param("password", "patient123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String start(MockHttpSession session) throws Exception {
        String body = mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.question.id");
    }

    private String answer(MockHttpSession session, String questionId, String answer, String answerSource)
            throws Exception {
        String json = answerSource == null
                ? "{\"questionId\":\"" + questionId + "\",\"answer\":\"" + answer + "\"}"
                : "{\"questionId\":\"" + questionId + "\",\"answer\":\"" + answer
                + "\",\"answerSource\":\"" + answerSource + "\"}";
        return mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ClinicalConversationResult resultOf(MockHttpSession session) {
        return (ClinicalConversationResult) session.getAttribute("clinicalConversationResult");
    }

    @Test
    void voiceAnswerEntersTheSamePipelineAndRedFlagsEvaluateTheRawAnswer() throws Exception {
        MockHttpSession session = loginPatient();
        String chief = start(session);

        String response = answer(session, chief, "I am struggling to breathe", "VOICE");

        assertThat((String) JsonPath.read(response, "$.question.id")).isEqualTo("hpi_onset");
        assertThat((String) JsonPath.read(response, "$.question.section"))
                .isEqualTo("HISTORY_OF_PRESENT_ILLNESS");
        assertThat((Integer) JsonPath.read(response, "$.redFlags.length()")).isEqualTo(1);
        assertThat((String) JsonPath.read(response, "$.redFlags[0].id")).isEqualTo("red_flag_breathing");
        assertThat((String) JsonPath.read(response, "$.urgency")).isEqualTo("URGENT");

        ClinicalConversationResult captured = resultOf(session);
        assertThat(captured.size()).isEqualTo(1);
        ClinicalAnswer recorded = captured.all().get(0);
        assertThat(recorded.answer()).isEqualTo("I am struggling to breathe");
        assertThat(recorded.answerSource()).isEqualTo(AnswerSource.VOICE);
        assertThat(recorded.questionId()).isEqualTo(chief);
    }

    @Test
    void typedAnswerDefaultsToTextSource() throws Exception {
        MockHttpSession session = loginPatient();
        String chief = start(session);

        answer(session, chief, "A mild headache", null);

        assertThat(resultOf(session).all().get(0).answerSource()).isEqualTo(AnswerSource.TEXT);
    }

    @Test
    void unknownAnswerSourceFailsSafeToText() throws Exception {
        MockHttpSession session = loginPatient();
        String chief = start(session);

        answer(session, chief, "A mild headache", "HACKED");

        assertThat(resultOf(session).all().get(0).answerSource()).isEqualTo(AnswerSource.TEXT);
    }

    @Test
    void voiceAnswerLeavesProgressionAndCompletionPlannerControlled() throws Exception {
        MockHttpSession session = loginPatient();
        String current = start(session);
        int answered = 0;
        boolean completed = false;
        while (current != null) {
            String response = answer(session, current, "a normal patient description", "VOICE");
            answered++;
            if (Boolean.TRUE.equals(JsonPath.read(response, "$.completed"))) {
                assertThat((String) JsonPath.read(response, "$.caseId")).isNotBlank();
                completed = true;
                break;
            }
            current = JsonPath.read(response, "$.question.id");
        }
        assertThat(completed).isTrue();

        assertThat(answered).isEqualTo(25);
        ClinicalConversationResult captured = resultOf(session);
        assertThat(captured.size()).isEqualTo(25);
        assertThat(captured.all()).allMatch(answer -> answer.answerSource() == AnswerSource.VOICE);
    }
}