package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
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
 * End-to-end clinical-safety test for M3 voice+language: a full intake
 * conversation conducted in Telugu (one answer entered by voice) persists every
 * answer with the correct patient-facing language, without altering question
 * content, ordering, or completion.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClinicalVoiceLanguageIntegrationTests {

    private static final String ORDINARY = "a normal patient description";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompletedCasePersistenceService persistence;

    @Test
    void teluguVoicePlusTypedIntakePersistsEveryAnswerWithTheSelectedLanguage() throws Exception {
        MockHttpSession session = loginAsPatient();
        String caseId = runFullIntakeInTelugu(session);

        CompletedCase completed = persistence.findByCaseId(caseId).orElseThrow();
        assertThat(completed.result().all()).hasSize(25);
        assertThat(completed.result().all())
                .allSatisfy(answer -> assertThat(answer.language()).isEqualTo("te-IN"));
        assertThat(completed.result().all().get(0).answerSource())
                .isEqualTo(in.devmedi.kiosk.module.clinical.dialogue.AnswerSource.VOICE);
        assertThat(completed.result().all().get(1).answerSource())
                .isEqualTo(in.devmedi.kiosk.module.clinical.dialogue.AnswerSource.TEXT);
        assertThat(completed.result().all())
                .extracting(in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer::questionId)
                .containsSubsequence(
                        "chief_complaint_symptom", "hpi_onset",
                        "dashavidha_prakriti", "dashavidha_vaya",
                        "ahara_vihara_ahara", "ahara_vihara_habits");
    }

    private String runFullIntakeInTelugu(MockHttpSession session) throws Exception {
        MvcResult start = mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String current = JsonPath.read(start.getResponse().getContentAsString(), "$.question.id");

        mockMvc.perform(post("/patient/intake/language")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"te-IN\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        String caseId = null;
        int answered = 0;
        while (current != null) {
            String source = answered == 0 ? "VOICE" : "TEXT";
            MvcResult response = mockMvc.perform(post("/patient/intake/conversation/answer")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"questionId\":\"" + current + "\",\"answer\":\""
                                    + ORDINARY + "\",\"answerSource\":\"" + source + "\"}")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andReturn();
            answered++;
            String body = response.getResponse().getContentAsString();
            if (Boolean.TRUE.equals(JsonPath.read(body, "$.completed"))) {
                caseId = JsonPath.read(body, "$.caseId");
                break;
            }
            current = JsonPath.read(body, "$.question.id");
        }

        assertThat(answered).isEqualTo(25);
        assertThat(caseId).isNotBlank();
        return caseId;
    }

    private MockHttpSession loginAsPatient() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", "patient")
                        .param("password", "patient123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}