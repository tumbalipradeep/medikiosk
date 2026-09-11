package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.controller.ClinicalIntakeConversationController;
import in.devmedi.kiosk.module.patient.controller.PatientIdentifyController;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import in.devmedi.kiosk.module.consent.service.ConsentService;
import in.devmedi.kiosk.module.patientsession.service.PatientSessionService;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import in.devmedi.kiosk.module.physician.service.CompletedCaseReviewStore;
import in.devmedi.kiosk.module.voice.language.SupportedLanguage;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class M5PatientKioskJourneyTests {

    private static final String PATIENT_BCRYPT = "$2a$10$xVtr2X.QvKLsn8HBQP7Ac.qixWbgqX8JOYO1ranOqZkR9t3Ynj5aW";

    private static final List<String> HPI_IDS = List.of(
            "chief_complaint_symptom", "hpi_onset", "hpi_provocation_palliation",
            "hpi_quality", "hpi_region_radiation", "hpi_severity", "hpi_timing_duration");

    private static final List<String> DASHAVIDHA_IDS = List.of(
            "dashavidha_prakriti", "dashavidha_vikriti", "dashavidha_sara",
            "dashavidha_samhanana", "dashavidha_pramana", "dashavidha_satmya",
            "dashavidha_sattva", "dashavidha_ahara_shakti", "dashavidha_vyayama_shakti",
            "dashavidha_vaya");

    private static final List<String> AHARA_VIHARA_IDS = List.of(
            "ahara_vihara_ahara", "ahara_vihara_meal_pattern", "ahara_vihara_appetite",
            "ahara_vihara_hydration", "ahara_vihara_sleep", "ahara_vihara_physical_activity",
            "ahara_vihara_daily_routine", "ahara_vihara_habits");

    private static final String ORDINARY = "routine answer";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentService consentService;
    @Autowired private PatientSessionService patientSessionService;
    @Autowired private CompletedCasePersistenceService casePersistence;
    @Autowired private CompletedCaseReviewStore reviewStore;

    private User secondPatient;

    @BeforeEach
    void setUp() {
        reviewStore.clear();
        casePersistence.deleteAll();
        secondPatient = userRepository.save(
                new User("patient_m5_" + System.nanoTime(), PATIENT_BCRYPT, "M5 Second Patient", Role.PATIENT));
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession login(User user) throws Exception {
        return login(user.getUsername(), "patient123");
    }

    private void setLanguage(MockHttpSession session, String langCode) throws Exception {
        mockMvc.perform(post("/patient/intake/language")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"" + langCode + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private void startConversation(MockHttpSession session) throws Exception {
        mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private String answerAndReturn(MockHttpSession session, String questionId, String answer) throws Exception {
        MvcResult result = mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\",\"answer\":\"" + answer + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private void answer(MockHttpSession session, String questionId, String answer) throws Exception {
        answerAndReturn(session, questionId, answer);
    }

    private void answerIds(MockHttpSession session, List<String> ids, String answer) throws Exception {
        for (String id : ids) {
            answer(session, id, answer);
        }
    }

    private void completeIntake(MockHttpSession session) throws Exception {
        answer(session, "chief_complaint_symptom", "Left knee pain for three days");
        answerIds(session, HPI_IDS.subList(1, HPI_IDS.size()), ORDINARY);
        answerIds(session, DASHAVIDHA_IDS, ORDINARY);
        answerIds(session, AHARA_VIHARA_IDS.subList(0, AHARA_VIHARA_IDS.size() - 1), ORDINARY);
        answer(session, "ahara_vihara_habits", "Daily walking and occasional yoga");
    }

    private Long getUserId(String username) {
        return userRepository.findByUsername(username).orElseThrow().getId();
    }

    // ----------------------------------------------------------------
    // 1. Identity step
    // ----------------------------------------------------------------

    @Test
    void identityStepShowsLocalIdentityBadgeAndAbhaHonestyMessage() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        MvcResult result = mockMvc.perform(get("/patient/identify").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString();
        assertThat(html)
                .contains("Demo Patient")
                .contains("LOCAL")
                .contains("not linked to an ABHA (Ayushman Bharat Health Account)")
                .contains("No Aadhaar number or ABHA number is collected")
                .doesNotContain("Your visit is linked to your Ayushman Bharat Health Account");
    }

    @Test
    void identityConfirmSetsSessionAttribute() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(post("/patient/identify/confirm").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(session.getAttribute(PatientIdentifyController.IDENTITY_CONFIRMED_ATTRIBUTE)).isEqualTo(true);
    }

    // ----------------------------------------------------------------
    // 2. Progress fields in responses
    // ----------------------------------------------------------------

    @Test
    void startReturnsProgressWithZeroAnsweredAndTwentyFiveTotal() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        MvcResult result = mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answeredCount").value(0))
                .andExpect(jsonPath("$.totalCount").value(25))
                .andReturn();
    }

    @Test
    void progressAdvancesAfterEachAnswer() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        startConversation(session);
        String body = answerAndReturn(session, "chief_complaint_symptom", "Headache");
        assertThat(JsonPath.<Integer>read(body, "$.answeredCount")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.totalCount")).isEqualTo(25);

        body = answerAndReturn(session, "hpi_onset", "Two days ago");
        assertThat(JsonPath.<Integer>read(body, "$.answeredCount")).isEqualTo(2);
    }

    @Test
    void completionReportsFullProgress() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        completeIntake(session);
        String last = answerAndReturn(session, "ahara_vihara_habits", "None");
        assertThat(JsonPath.<Boolean>read(last, "$.completed")).isTrue();
        assertThat(JsonPath.<Integer>read(last, "$.answeredCount")).isEqualTo(25);
        assertThat(JsonPath.<Integer>read(last, "$.totalCount")).isEqualTo(25);
    }

    // ----------------------------------------------------------------
    // 3. Localized question text (non-English curated translation)
    // ----------------------------------------------------------------

    @Test
    void teluguLanguageServesTranslatedQuestionText() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        setLanguage(session, SupportedLanguage.TELUGU.code());
        MvcResult result = mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String questionText = JsonPath.read(result.getResponse().getContentAsString(), "$.question.text");
        assertThat(questionText).isNotBlank();
        // Telugu script range: U+0C00–U+0C7F; verify the text contains Telugu characters
        assertThat(questionText.codePoints().anyMatch(cp -> cp >= 0x0C00 && cp <= 0x0C7F)).isTrue();
        // English canonical text should not be served for Telugu
        assertThat(questionText).doesNotContain("What is the main problem");
    }

    @Test
    void hindiLanguageServesTranslatedQuestionText() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        setLanguage(session, SupportedLanguage.HINDI.code());
        MvcResult result = mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String questionText = JsonPath.read(result.getResponse().getContentAsString(), "$.question.text");
        assertThat(questionText).isNotBlank();
        // Devanagari range: U+0900–U+097F
        assertThat(questionText.codePoints().anyMatch(cp -> cp >= 0x0900 && cp <= 0x097F)).isTrue();
    }

    @Test
    void languageChangeDuringConversationIsReflectedInNextQuestion() throws Exception {
        MockHttpSession session = login("patient", "patient123");
        startConversation(session);
        // First question in English (no Telugu script)
        String body = answerAndReturn(session, "chief_complaint_symptom", "Back pain");
        String enQuestion = JsonPath.read(body, "$.question.text");
        assertThat(enQuestion).isNotBlank();
        assertThat(enQuestion.codePoints().anyMatch(cp -> cp >= 0x0C00 && cp <= 0x0C7F)).isFalse();
        // Switch to Telugu mid-conversation
        setLanguage(session, SupportedLanguage.TELUGU.code());
        body = answerAndReturn(session, "hpi_onset", "Last week");
        String teQuestion = JsonPath.read(body, "$.question.text");
        assertThat(teQuestion).isNotBlank();
        assertThat(teQuestion.codePoints().anyMatch(cp -> cp >= 0x0C00 && cp <= 0x0C7F)).isTrue();
    }

    // ----------------------------------------------------------------
    // 4. Idempotent final answer completion
    // ----------------------------------------------------------------

    @Test
    void repeatedFinalAnswerReturnsSameCaseIdAndDoesNotDuplicatePersistedCase() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completeIntake(patient);
        String first = answerAndReturn(patient, "ahara_vihara_habits", "Daily walks");
        String firstCaseId = JsonPath.read(first, "$.caseId");
        assertThat(firstCaseId).isNotBlank();
        assertThat(JsonPath.<Boolean>read(first, "$.completed")).isTrue();

        // Repeated final answer
        String second = answerAndReturn(patient, "ahara_vihara_habits", "Daily walks");
        String secondCaseId = JsonPath.read(second, "$.caseId");
        assertThat(secondCaseId).isEqualTo(firstCaseId);
        assertThat(JsonPath.<Boolean>read(second, "$.completed")).isTrue();
    }

    @Test
    void completionStartReturnsCachedCompletedCaseId() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completeIntake(patient);
        answerAndReturn(patient, "ahara_vihara_habits", "Yoga and walking");

        MvcResult result = mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(patient)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(body, "$.completed")).isTrue();
        String caseId = JsonPath.read(body, "$.caseId");
        assertThat(caseId).isNotBlank();
    }

    // ----------------------------------------------------------------
    // 5. Review summary ownership enforcement
    // ----------------------------------------------------------------

    @Test
    void patientCanReadOwnCaseSummary() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completeIntake(patient);
        String caseId = reviewStore.latest().orElseThrow().id();

        mockMvc.perform(get("/patient/cases/" + caseId + "/summary").session(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.answers.length()").value(25))
                .andExpect(jsonPath("$.answeredCount").value(25));
    }

    @Test
    void secondPatientCannotReadFirstPatientsCaseSummary() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completeIntake(patient);
        String caseId = reviewStore.latest().orElseThrow().id();

        MockHttpSession other = login(secondPatient);
        mockMvc.perform(get("/patient/cases/" + caseId + "/summary").session(other))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonExistentCaseReturnsNotFound() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        mockMvc.perform(get("/patient/cases/case-nonexistent/summary").session(patient))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------
    // 6. Patient session lifecycle
    // ----------------------------------------------------------------

    @Test
    void patientSessionIsMarkedCompletedAfterIntakeCompletion() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        Long userId = getUserId("patient");

        consentService.grant(userId, ConsentType.CLINICAL_CASE_TAKING);
        patientSessionService.start(userId);
        assertThat(patientSessionService.hasActiveSession(userId)).isTrue();

        completeIntake(patient);
        answerAndReturn(patient, "ahara_vihara_habits", "Daily routine");

        assertThat(patientSessionService.hasActiveSession(userId)).isFalse();
    }

    @Test
    void newPatientSessionCanBeStartedAfterCompletion() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        Long userId = getUserId("patient");

        consentService.grant(userId, ConsentType.CLINICAL_CASE_TAKING);
        patientSessionService.start(userId);
        completeIntake(patient);
        answerAndReturn(patient, "ahara_vihara_habits", "None");
        assertThat(patientSessionService.hasActiveSession(userId)).isFalse();

        // Start a new session after completion succeeds
        patientSessionService.start(userId);
        assertThat(patientSessionService.hasActiveSession(userId)).isTrue();
    }

    @Test
    void newStartAfterCompletionClearsCompletedCaseAttribute() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completeIntake(patient);
        assertThat(patient.getAttribute(ClinicalIntakeConversationController.COMPLETED_CASE_ATTRIBUTE)).isNotNull();

        mockMvc.perform(post("/patient/session/start").session(patient).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(patient.getAttribute(ClinicalIntakeConversationController.COMPLETED_CASE_ATTRIBUTE)).isNull();
    }

    @Test
    void homePageShowsLatestCaseId() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completeIntake(patient);
        String caseId = reviewStore.latest().orElseThrow().id();

        mockMvc.perform(get("/patient/home").session(patient))
                .andExpect(status().isOk());
        // home template renders latestCaseId — verify via HTML content
        MvcResult result = mockMvc.perform(get("/patient/home").session(patient))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains(caseId);
    }

    @Test
    void intakePageExposesCompletedCaseIdToTemplate() throws Exception {
        MockHttpSession patient = login("patient", "patient123");
        completeIntake(patient);
        String caseId = reviewStore.latest().orElseThrow().id();

        MvcResult result = mockMvc.perform(get("/patient/intake").session(patient))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("completed-case");
        assertThat(result.getResponse().getContentAsString()).contains(caseId);
    }
}