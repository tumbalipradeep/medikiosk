package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryItem;
import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdaptiveHistoryConversationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClinicalHistoryItemRepository historyRepository;

    private Long patientId;

    @BeforeEach
    void resetPatientHistory() {
        patientId = userRepository.findByUsername("patient").orElseThrow().getId();
        historyRepository.deleteAll(historyRepository.findByPatientIdOrderByCategoryAscConceptKeyAsc(patientId));
    }

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
    void adaptivePageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/patient/history/adaptive"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void nonPatientRoleCannotStartAdaptiveConversation() throws Exception {
        MockHttpSession session = login("physician", "physician123");
        mockMvc.perform(post("/patient/history/adaptive/start").session(session).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfIsEnforcedOnAdaptiveEndpoints() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(post("/patient/history/adaptive/start").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/patient/history/adaptive/answer").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void adaptivePageIsVisibleToPatient() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(get("/patient/history/adaptive").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Complete your clinical history")));
    }

    @Test
    void fullAdaptiveConversationPersistsCompleteHistory() throws Exception {
        MockHttpSession session = login("patient", "patient123");

        MvcResult start = mockMvc.perform(post("/patient/history/adaptive/start")
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.started").value(true))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.questionId").value("pmh_chronic_illness"))
                .andReturn();

        String questionId = JsonPath.read(start.getResponse().getContentAsString(), "$.questionId");
        int loops = 0;
        boolean completed = false;
        while (loops++ < 60) {
            MvcResult result = mockMvc.perform(post("/patient/history/adaptive/answer")
                            .session(session).with(csrf())
                            .contentType("application/json")
                            .content("{\"questionId\":\"" + questionId + "\",\"answer\":\"Yes - as I described.\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            Boolean done = JsonPath.read(result.getResponse().getContentAsString(), "$.completed");
            if (Boolean.TRUE.equals(done)) {
                completed = true;
                break;
            }
            questionId = JsonPath.read(result.getResponse().getContentAsString(), "$.questionId");
        }

        assertThat(completed).as("conversation should complete").isTrue();
        long persisted = historyRepository.countByPatientId(patientId);
        assertThat(persisted).isPositive();
        assertThat(historyRepository.findByPatientIdOrderByCategoryAscConceptKeyAsc(patientId))
                .allMatch(item -> item.getValue() != null && !item.getValue().isBlank());
    }

    @Test
    void answeringOutOfSequenceIsRejected() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(post("/patient/history/adaptive/start").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/patient/history/adaptive/answer")
                        .session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"questionId\":\"wrong-id\",\"answer\":\"x\"}"))
                .andExpect(status().isBadRequest());

        ClinicalHistoryItem any = historyRepository.findByPatientIdOrderByCategoryAscConceptKeyAsc(patientId)
                .stream().findFirst().orElse(null);
        assertThat(any).isNull();
    }
}