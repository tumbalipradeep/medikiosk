package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.document.service.ClinicalDocumentService;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import in.devmedi.kiosk.module.physician.service.CompletedCaseReviewStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PhysicianReviewControllerIntegrationTests {

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompletedCaseReviewStore reviewStore;

    @Autowired
    private CompletedCasePersistenceService casePersistence;

    @BeforeEach
    void clearPersistedData() {
        reviewStore.clear();
        casePersistence.deleteAll();
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

    private void startConversation(MockHttpSession session) throws Exception {
        mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.id").value("chief_complaint_symptom"));
    }

    private void answer(MockHttpSession session, String questionId, String answerText) throws Exception {
        mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\",\"answer\":\""
                                + answerText.replace("\"", "\\\"") + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private void answerIds(MockHttpSession session, List<String> ids, String answerText) throws Exception {
        for (String id : ids) {
            answer(session, id, answerText);
        }
    }

    private void completePatientIntake(MockHttpSession session) throws Exception {
        String chief = "Nagging pain in my left knee";
        String dashavidha = "Sturdy build, feels warm most of the time";
        String habits = "Occasional evening walking; daily morning yoga";
        startConversation(session);
        answer(session, "chief_complaint_symptom", chief);
        answerIds(session, HPI_IDS.subList(1, HPI_IDS.size()), ORDINARY);
        answer(session, "dashavidha_prakriti", dashavidha);
        answerIds(session, DASHAVIDHA_IDS.subList(1, DASHAVIDHA_IDS.size()), ORDINARY);
        answerIds(session, AHARA_VIHARA_IDS.subList(0, AHARA_VIHARA_IDS.size() - 1), ORDINARY);
        answer(session, "ahara_vihara_habits", habits);
    }

    @Test
    void completedPatientIntakeIsRegisteredInTheReviewStore() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completePatientIntake(patient);

        assertThat(reviewStore.latest()).isPresent();
        assertThat(reviewStore.latest().orElseThrow().result().size()).isEqualTo(25);
        assertThat(reviewStore.latest().orElseThrow().result().all())
                .extracting(entry -> entry.answer())
                .contains("Nagging pain in my left knee", "Occasional evening walking; daily morning yoga");
    }

    @Test
    void completedIntakeRegistersACaseWithAStableIdentity() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completePatientIntake(patient);

        CompletedCase completed = reviewStore.latest().orElseThrow();
        assertThat(completed.id()).startsWith("case-");
        assertThat(completed.result()).isNotNull();
        assertThat(completed.result().size()).isEqualTo(25);
    }

    @Test
    void caseIdentityIsStableWhileHeldInTheStore() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completePatientIntake(patient);

        CompletedCase first = reviewStore.latest().orElseThrow();
        CompletedCase second = reviewStore.latest().orElseThrow();

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.result()).isSameAs(first.result());
    }

    @Test
    void aNewCompletedIntakeReplacesTheCaseWithANewIdentity() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completePatientIntake(patient);
        String firstCaseId = reviewStore.latest().orElseThrow().id();

        completePatientIntake(patient);
        CompletedCase replaced = reviewStore.latest().orElseThrow();

        assertThat(replaced.id()).isNotEqualTo(firstCaseId);
        assertThat(replaced.result().size()).isEqualTo(25);
    }

    @Test
    void noCaseIdentityWhenStoreIsEmpty() throws Exception {

        assertThat(reviewStore.latest()).isEmpty();

        MockHttpSession physician = login("physician", "physician123");
        mockMvc.perform(get("/physician/review").session(physician))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No reviewable intake yet")));
    }

    @Test
    void physicianReviewReadsTheRegisteredCompletedCase() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completePatientIntake(patient);

        MockHttpSession physician = login("physician", "physician123");
        mockMvc.perform(get("/physician/review").session(physician))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("History of Present Illness / SOCRATES")))
                .andExpect(content().string(containsString("Dashavidha Pariksha")))
                .andExpect(content().string(containsString("Ahara-Vihara")))
                .andExpect(content().string(containsString("25 captured answers")))
                .andExpect(content().string(containsString("Nagging pain in my left knee")))
                .andExpect(content().string(containsString("Sturdy build, feels warm most of the time")))
                .andExpect(content().string(containsString("Occasional evening walking; daily morning yoga")))
                .andExpect(content().string(not(containsString("Severe headache"))))
                .andExpect(content().string(containsString("mark-reviewed-btn")))
                .andExpect(content().string(containsString("completeReviewBtn")));
    }

    @Test
    void physicianReviewShowsEmptyStateWhenNoCompletedCase() throws Exception {
        MockHttpSession physician = login("physician", "physician123");
        mockMvc.perform(get("/physician/review").session(physician))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No reviewable intake yet")))
                .andExpect(content().string(not(containsString("Dashavidha Pariksha"))))
                .andExpect(content().string(not(containsString("mark-reviewed-btn"))))
                .andExpect(content().string(not(containsString("completeReviewBtn"))));
    }

    @Test
    void physicianReviewPageExposesTheCompletedCaseIdentity() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completePatientIntake(patient);
        String caseId = reviewStore.latest().orElseThrow().id();

        MockHttpSession physician = login("physician", "physician123");
        mockMvc.perform(get("/physician/review").session(physician))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Case " + caseId)));
    }

    @Test
    void patientCannotAccessReviewPage() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(get("/physician/review").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void intakeCompletionResponseCarriesTheCaseIdentity() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        startConversation(patient);
        answerIds(patient, List.of(
                "chief_complaint_symptom",
                "hpi_onset",
                "hpi_provocation_palliation",
                "hpi_quality",
                "hpi_region_radiation",
                "hpi_severity",
                "hpi_timing_duration"), ORDINARY);
        answerIds(patient, DASHAVIDHA_IDS, ORDINARY);
        answerIds(patient, AHARA_VIHARA_IDS.subList(0, AHARA_VIHARA_IDS.size() - 1), ORDINARY);

        MvcResult result = mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(patient)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"ahara_vihara_habits\",\"answer\":\"daily walks\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andReturn();
        String returnedCaseId = JsonPath.read(result.getResponse().getContentAsString(), "$.caseId");

        String registeredCaseId = reviewStore.latest().orElseThrow().id();
        assertThat(returnedCaseId).isEqualTo(registeredCaseId);
        assertThat(returnedCaseId).startsWith("case-");
    }

    @Test
    void physicianReviewShowsAttachedDocumentsMetadata() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completePatientIntake(patient);
        String caseId = reviewStore.latest().orElseThrow().id();

        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(new MockMultipartFile("file", "xray-report.pdf", "application/pdf",
                                new byte[]{0x25, 0x50, 0x44, 0x46, 1}))
                        .session(patient)
                        .with(csrf()))
                .andExpect(status().isOk());

        MockHttpSession physician = login("physician", "physician123");
        mockMvc.perform(get("/physician/review").session(physician))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Clinical Documents")))
                .andExpect(content().string(containsString("xray-report.pdf")))
                .andExpect(content().string(containsString("application/pdf")));
    }

    @Test
    void unauthenticatedAccessToReviewPageIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/physician/review"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}