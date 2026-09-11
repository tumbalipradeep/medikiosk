package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.ai.conversation.AiQuestionValidator;
import in.devmedi.kiosk.module.ai.provider.AiFailoverService;
import in.devmedi.kiosk.module.clinical.ai.AiConversationService;
import in.devmedi.kiosk.module.clinical.ai.NextQuestionWording;
import in.devmedi.kiosk.module.clinical.controller.ClinicalIntakeConversationController;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that a validated AI provider can rephrase the next deterministic
 * question in the live intake conversation while the deterministic backbone
 * (question id, section, ordering), red-flag evaluation, and the patient's raw
 * answer all remain authoritative and untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AiConversationControllerIntegrationTests.FakeAiConfig.class)
class AiConversationControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aiWordingIsUsedButIdSectionAndRedFlagsStayDeterministic() throws Exception {
        MockHttpSession session = patientSession();
        String startResponse = mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String chief = JsonPath.read(startResponse, "$.question.id");

        MvcResult result = mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + chief + "\",\"answer\":\"I am struggling to breathe\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();

        // The AI rephrased the next question's text...
        assertThat((String) JsonPath.read(response, "$.question.text"))
                .isEqualTo("AI phrase: When did it first start?");

        // ...but the deterministic id, section, and type are authoritative.
        assertThat((String) JsonPath.read(response, "$.question.id")).isEqualTo("hpi_onset");
        assertThat((String) JsonPath.read(response, "$.question.section")).isEqualTo("HISTORY_OF_PRESENT_ILLNESS");
        assertThat((String) JsonPath.read(response, "$.question.type")).isEqualTo("ONSET");

        // Red flags are evaluated deterministically and independently of AI.
        assertThat((List<?>) JsonPath.read(response, "$.redFlags")).isNotEmpty();
        assertThat((String) JsonPath.read(response, "$.redFlags[0].id")).isEqualTo("red_flag_breathing");

        // The patient's raw answer is the source of truth recorded in the session.
        ClinicalConversationResult captured = resultOf(session);
        assertThat(captured.size()).isEqualTo(1);
        ClinicalAnswer recorded = captured.all().get(0);
        assertThat(recorded.answer()).isEqualTo("I am struggling to breathe");
        assertThat(recorded.questionText())
                .isEqualTo("What is the main problem or symptom that brought you here today?");
        assertThat(recorded.questionId()).isEqualTo("chief_complaint_symptom");

        assertThat(FakeAiConfig.AI_CALLS.get()).isPositive();
    }

    @Test
    void displayedAiWordingAndSourceAreRecordedAlongsideCanonicalQuestion() throws Exception {
        MockHttpSession session = patientSession();
        String startResponse = mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String chief = JsonPath.read(startResponse, "$.question.id");

        MvcResult first = mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + chief + "\",\"answer\":\"My stomach hurts\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"hpi_onset\",\"answer\":\"Three days ago\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        // The AI rephrases the next question on every step...
        assertThat((String) JsonPath.read(first.getResponse().getContentAsString(), "$.question.text"))
                .isEqualTo("AI phrase: When did it first start?");
        assertThat((String) JsonPath.read(second.getResponse().getContentAsString(), "$.question.text"))
                .startsWith("AI phrase: ");

        // Clinical record preserves canonical+displayed+source+verbatim answer truthfully.
        ClinicalConversationResult captured = resultOf(session);
        assertThat(captured.size()).isEqualTo(2);

        ClinicalAnswer chiefAnswer = captured.all().get(0);
        assertThat(chiefAnswer.questionText())
                .isEqualTo("What is the main problem or symptom that brought you here today?");
        assertThat(chiefAnswer.displayedQuestionText())
                .isEqualTo(chiefAnswer.questionText());
        assertThat(chiefAnswer.questionSource()).isEqualTo(in.devmedi.kiosk.module.clinical.dialogue.QuestionSource.DETERMINISTIC);
        assertThat(chiefAnswer.answer()).isEqualTo("My stomach hurts");

        ClinicalAnswer onsetAnswer = captured.all().get(1);
        assertThat(onsetAnswer.questionId()).isEqualTo("hpi_onset");
        assertThat(onsetAnswer.questionText()).isEqualTo("When did it first start?");
        assertThat(onsetAnswer.displayedQuestionText()).isEqualTo("AI phrase: When did it first start?");
        assertThat(onsetAnswer.questionSource())
                .isEqualTo(in.devmedi.kiosk.module.clinical.dialogue.QuestionSource.AI_GENERATED);
        assertThat(onsetAnswer.answer()).isEqualTo("Three days ago");
    }

    @Test
    void aiRephrasingLeavesProgressionAndCompletionIntact() throws Exception {
        MockHttpSession session = patientSession();
        String startResponse = mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String current = JsonPath.read(startResponse, "$.question.id");
        int answered = 0;
        while (current != null) {
            String response = mockMvc.perform(post("/patient/intake/conversation/answer")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"questionId\":\"" + current + "\",\"answer\":\"a normal patient description\"}")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            answered++;
            if (Boolean.TRUE.equals(JsonPath.read(response, "$.completed"))) {
                break;
            }
            current = JsonPath.read(response, "$.question.id");
        }

        // 7 HPI + 10 Dashavidha + 8 Ahara-Vihara deterministically, then completion.
        assertThat(answered).isEqualTo(25);
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

    private ClinicalConversationResult resultOf(MockHttpSession session) {
        return (ClinicalConversationResult) session.getAttribute(
                ClinicalIntakeConversationController.RESULT_ATTRIBUTE);
    }

    @TestConfiguration
    static class FakeAiConfig {

        static final AtomicInteger AI_CALLS = new AtomicInteger();

        @Bean
        @Primary
        AiConversationService fakeAiConversationService(AiFailoverService failoverService,
                                                        AiQuestionValidator validator) {
            return new AiConversationService(failoverService, validator) {
                @Override
                public NextQuestionWording nextQuestionWording(String section,
                                                               String questionTopic,
                                                               String targetQuestionText,
                                                               String latestAnswer,
                                                               List<ClinicalAnswer> recentAnswers,
                                                               String language) {
                    AI_CALLS.incrementAndGet();
                    return NextQuestionWording.aiGenerated("AI phrase: " + targetQuestionText, "groq", "fake-model", 1L);
                }
            };
        }
    }
}