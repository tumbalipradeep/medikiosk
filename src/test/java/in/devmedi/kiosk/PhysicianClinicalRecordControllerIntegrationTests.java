package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.clinical.redflag.RedFlag;
import in.devmedi.kiosk.module.clinical.redflag.RedFlagSeverity;
import in.devmedi.kiosk.module.clinical.triage.FlagAssessmentAction;
import in.devmedi.kiosk.module.clinical.triage.RedFlagAssessmentRepository;
import in.devmedi.kiosk.module.clinical.triage.RedFlagAssessmentService;
import in.devmedi.kiosk.module.clinical.triage.TriageSource;
import in.devmedi.kiosk.module.physician.clinicalrecord.ClinicalRecordStatus;
import in.devmedi.kiosk.module.physician.clinicalrecord.ClinicalSummary;
import in.devmedi.kiosk.module.physician.clinicalrecord.ClinicalSummaryRepository;
import in.devmedi.kiosk.module.physician.clinicalrecord.ClinicalSummaryService;
import in.devmedi.kiosk.module.physician.clinicalrecord.Consultation;
import in.devmedi.kiosk.module.physician.clinicalrecord.ConsultationRepository;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import in.devmedi.kiosk.module.physician.service.CompletedCaseReviewStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PhysicianClinicalRecordControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CompletedCaseReviewStore reviewStore;
    @Autowired private CompletedCasePersistenceService casePersistence;
    @Autowired private ClinicalSummaryRepository summaryRepository;
    @Autowired private ClinicalSummaryService summaryService;
    @Autowired private RedFlagAssessmentService triageService;
    @Autowired private RedFlagAssessmentRepository assessmentRepository;
    @Autowired private ConsultationRepository consultationRepository;

    private Long patientUserId;

    @BeforeEach
    void setUp() {
        reviewStore.clear();
        casePersistence.deleteAll();
        summaryRepository.deleteAll();
        consultationRepository.deleteAll();
        assessmentRepository.deleteAll();
        patientUserId = userRepository.findByUsername("patient").orElseThrow().getId();
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

    private String createCase(String chiefComplaint) {
        ClinicalConversationResult result = new ClinicalConversationResult();
        result.record(new ClinicalAnswer("chief_complaint_symptom", "CHIEF_COMPLAINT",
                "SYMPTOM_PROBLEM", "Describe your problem", chiefComplaint));
        result.record(new ClinicalAnswer("hpi_onset", "HISTORY_OF_PRESENT_ILLNESS",
                "SYMPTOM_ONSET", "When did it start?", "This morning around 6am"));
        CompletedCase completed = CompletedCase.withNewId(result, patientUserId);
        casePersistence.save(completed, patientUserId);
        return completed.id();
    }

    @Test
    void recordPageRequiresAuthentication() throws Exception {
        String caseId = createCase("Mild knee pain for three days");
        mockMvc.perform(get("/physician/cases/" + caseId + "/record"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void patientCannotOpenTheRecord() throws Exception {
        String caseId = createCase("Mild knee pain for three days");
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(get("/physician/cases/" + caseId + "/record").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordPageRendersForAnExistingCase() throws Exception {
        String caseId = createCase("Mild knee pain for three days");
        MockHttpSession session = login("physician", "physician123");
        mockMvc.perform(get("/physician/cases/" + caseId + "/record").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Red-Flag Triage")));
    }

    @Test
    void physicianTriagesUrgentFlag() throws Exception {
        String caseId = createCase("Crushing chest pain with severe difficulty breathing");
        triageService.recordSystemDetectedFlags(caseId, List.of(
                new RedFlag("red_flag_chest_pain", RedFlagSeverity.URGENT,
                        "Severe chest pain or pressure", "matches crushing chest pain"),
                new RedFlag("red_flag_breathing", RedFlagSeverity.URGENT,
                        "Severe breathing difficulty", "matches difficulty breathing")));

        String flagId = assessmentRepository.findByCompletedCase_CaseIdOrderByAssessedAtAsc(caseId)
                .stream().map(a -> a.getFlagId()).filter(id -> id.contains("chest_pain"))
                .findFirst().orElseThrow();

        MockHttpSession session = login("physician", "physician123");
        mockMvc.perform(post("/physician/cases/" + caseId + "/flags/" + flagId + "/action")
                        .session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"action\":\"ESCALATED\",\"note\":\"Review urgently\"}"))
                .andExpect(status().isOk());

        var assessment = assessmentRepository.findByCompletedCase_CaseIdAndFlagId(caseId, flagId).orElseThrow();
        assertThat(assessment.getAction()).isEqualTo(FlagAssessmentAction.ESCALATED);
        assertThat(assessment.getSource()).isEqualTo(TriageSource.PHYSICIAN_ASSESSMENT);
        assertThat(assessment.getNote()).isEqualTo("Review urgently");
    }

    @Test
    void invalidTriageActionIsRejected() throws Exception {
        String caseId = createCase("Mild knee pain for three days");
        triageService.recordSystemDetectedFlags(caseId, List.of(
                new RedFlag("red_flag_chest_pain", RedFlagSeverity.URGENT, "t", "m")));
        MockHttpSession session = login("physician", "physician123");
        mockMvc.perform(post("/physician/cases/" + caseId + "/flags/red_flag_chest_pain/action")
                        .session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"action\":\"NONSENSE\",\"note\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryCanBeAmendedAndAccepted() throws Exception {
        String caseId = createCase("Mild knee pain for three days");
        CompletedCase completed = completedCaseFor(caseId);
        summaryService.seedDrafts(completed);
        var seeded = summaryRepository.findByCompletedCase_CaseIdOrderBySectionAsc(caseId);
        assertThat(seeded).isNotEmpty();
        String section = seeded.get(0).getSection();
        assertThat(seeded.get(0).getStatus()).isEqualTo(ClinicalRecordStatus.DRAFT);

        MockHttpSession session = login("physician", "physician123");
        mockMvc.perform(post("/physician/cases/" + caseId + "/summary/" + section + "/amend")
                        .session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"content\":\"Amended summary text\"}"))
                .andExpect(status().isOk());
        assertThat(summaryRepository.findByCompletedCase_CaseIdAndSection(caseId, section).orElseThrow().getContent())
                .isEqualTo("Amended summary text");

        mockMvc.perform(post("/physician/cases/" + caseId + "/summary/" + section + "/accept")
                        .session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"content\":\"Amended summary text\"}"))
                .andExpect(status().isOk());
        assertThat(summaryRepository.findByCompletedCase_CaseIdAndSection(caseId, section).orElseThrow().getStatus())
                .isEqualTo(ClinicalRecordStatus.ACCEPTED);
    }

    private CompletedCase completedCaseFor(String caseId) {
        ClinicalConversationResult result = new ClinicalConversationResult();
        result.record(new ClinicalAnswer("chief_complaint_symptom", "CHIEF_COMPLAINT",
                "SYMPTOM_PROBLEM", "Describe your problem", "Mild knee pain for three days"));
        return new CompletedCase(caseId, result, patientUserId);
    }

    @Test
    void consultationCanBeSavedAndFinalized() throws Exception {
        String caseId = createCase("Mild knee pain for three days");
        MockHttpSession session = login("physician", "physician123");

        mockMvc.perform(post("/physician/cases/" + caseId + "/consultation")
                        .session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assessment\":\"Likely soft tissue strain\",\"plan\":\"Rest and review\",\"advice\":\"Monitor\",\"followUp\":\"1 week\"}"))
                .andExpect(status().isOk());

        Consultation consultation = consultationRepository.findByCompletedCase_CaseId(caseId).orElseThrow();
        assertThat(consultation.getAssessment()).isEqualTo("Likely soft tissue strain");
        assertThat(consultation.getStatus()).isEqualTo(ClinicalRecordStatus.DRAFT);

        mockMvc.perform(post("/physician/cases/" + caseId + "/consultation/finalize")
                        .session(session).with(csrf()))
                .andExpect(status().isOk());

        Consultation finalized = consultationRepository.findByCompletedCase_CaseId(caseId).orElseThrow();
        assertThat(finalized.getStatus()).isEqualTo(ClinicalRecordStatus.FINALIZED);
        assertThat(finalized.getFinalizedAt()).isNotNull();
    }

    @Test
    void recordPageForMissingCaseIsNotFound() throws Exception {
        MockHttpSession session = login("physician", "physician123");
        mockMvc.perform(get("/physician/cases/does-not-exist/record").session(session))
                .andExpect(status().isNotFound());
    }
}