package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryItemRepository;
import in.devmedi.kiosk.module.clinical.triage.RedFlagAssessmentRepository;
import in.devmedi.kiosk.module.clinical.triage.TriageSource;
import in.devmedi.kiosk.module.patient.controller.PatientIdentifyController;
import in.devmedi.kiosk.module.physician.clinicalrecord.ClinicalSummaryRepository;
import in.devmedi.kiosk.module.physician.clinicalrecord.ClinicalRecordStatus;
import in.devmedi.kiosk.module.physician.clinicalrecord.ClinicalSummary;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import in.devmedi.kiosk.module.physician.service.CompletedCaseReviewStore;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the intake completion wiring: a completed intake seeds draft
 * clinical-summary sections, structured clinical history, and persisted
 * red-flag assessments (only when flags were actually triggered).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClinicalRecordAutoSeedingIntegrationTests {

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
    @Autowired private CompletedCaseReviewStore reviewStore;
    @Autowired private CompletedCasePersistenceService casePersistence;
    @Autowired private ClinicalHistoryItemRepository historyRepository;
    @Autowired private ClinicalSummaryRepository summaryRepository;
    @Autowired private RedFlagAssessmentRepository assessmentRepository;

    private Long patientId;

    @BeforeEach
    void setUp() {
        reviewStore.clear();
        casePersistence.deleteAll();
        patientId = userRepository.findByUsername("patient").orElseThrow().getId();
        historyRepository.deleteAll(historyRepository.findByPatientIdOrderByCategoryAscConceptKeyAsc(patientId));
        summaryRepository.deleteAll();
        assessmentRepository.deleteAll();
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", "patient")
                        .param("password", "patient123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void start(MockHttpSession session) throws Exception {
        mockMvc.perform(post("/patient/intake/conversation/start")
                        .session(session).contentType(MediaType.APPLICATION_JSON).with(csrf()))
                .andExpect(status().isOk());
    }

    private String caseId(MockHttpSession session) throws Exception {
        String body = mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"hara_placeholder\",\"answer\":\"x\"}").with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.caseId");
    }

    private String answerAndReturn(MockHttpSession session, String questionId, String answer) throws Exception {
        MvcResult result = mockMvc.perform(post("/patient/intake/conversation/answer")
                        .session(session).contentType(MediaType.APPLICATION_JSON)
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

    private String completeIntake(String chiefComplaint, boolean urgent) throws Exception {
        MockHttpSession session = login();
        start(session);
        answer(session, "chief_complaint_symptom",
                urgent ? "Crushing chest pain with severe difficulty breathing" : chiefComplaint);
        answerIds(session, HPI_IDS.subList(1, HPI_IDS.size()), ORDINARY);
        answerIds(session, DASHAVIDHA_IDS, ORDINARY);
        answerIds(session, AHARA_VIHARA_IDS.subList(0, AHARA_VIHARA_IDS.size() - 1), ORDINARY);
        String finalResponse = answerAndReturn(session, "ahara_vihara_habits", "Daily walking and occasional yoga");
        return JsonPath.read(finalResponse, "$.caseId");
    }

    @Test
    void routineIntakeSeedsSummaryDraftsAndNoFlagAssessments() throws Exception {
        String caseId = completeIntake("Left knee pain for three days", false);

        assertThat(caseId).isNotBlank();
        List<ClinicalSummary> summaries = summaryRepository.findByCompletedCase_CaseIdOrderBySectionAsc(caseId);
        assertThat(summaries).isNotEmpty();
        assertThat(summaries).allMatch(s -> s.getStatus() == ClinicalRecordStatus.DRAFT);
        assertThat(summaries).allMatch(s -> s.getContent() != null && !s.getContent().isBlank());
        assertThat(assessmentRepository.findByCompletedCase_CaseIdOrderByAssessedAtAsc(caseId)).isEmpty();
        assertThat(historyRepository.countByPatientId(patientId)).isPositive();
    }

    @Test
    void urgentIntakeSeedsPersistedRedFlagAssessments() throws Exception {
        String caseId = completeIntake("Left knee pain", true);

        var assessments = assessmentRepository.findByCompletedCase_CaseIdOrderByAssessedAtAsc(caseId);
        assertThat(assessments).isNotEmpty();
        assertThat(assessments).allMatch(a -> a.getSource() == TriageSource.SYSTEM_DETECTED);
        assertThat(assessments.stream().map(a -> a.getFlagId()))
                .anyMatch(id -> id.contains("chest_pain") || id.contains("breathing") || id.contains("severity"));
        assertThat(summaryRepository.findByCompletedCase_CaseIdOrderBySectionAsc(caseId)).isNotEmpty();
    }
}