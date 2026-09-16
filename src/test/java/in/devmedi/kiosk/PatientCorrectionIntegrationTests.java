package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RD2 Phase 5 — patient correction loop.
 *
 * <p>Covers the directive's contract: a patient can correct ONLY their own
 * case's answers, the original captured answer is never modified, every
 * accepted correction is audited, physicians see original + correction
 * distinctly, and security (role boundary + CSRF) holds.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PatientCorrectionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompletedCasePersistenceService casePersistence;

    private Long patientUserId;
    private String caseId;

    @BeforeEach
    void setUp() {
        casePersistence.deleteAll();
        patientUserId = userRepository.findByUsername("patient").orElseThrow().getId();
        caseId = createCase("Persistent cough for two weeks");
    }

    // ─── Fixtures ─────────────────────────────────────────────────────

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession loginPatient() throws Exception {
        return login("patient", "patient123");
    }

    private MockHttpSession loginPhysician() throws Exception {
        return login("physician", "physician123");
    }

    private String createCase(String answer) {
        ClinicalConversationResult result = new ClinicalConversationResult();
        result.record(new ClinicalAnswer("chief_complaint_symptom", "CHIEF_COMPLAINT",
                "SYMPTOM_PROBLEM", "Describe your problem", answer));
        CompletedCase completed = CompletedCase.withNewId(result, patientUserId);
        casePersistence.save(completed, patientUserId);
        return completed.id();
    }

    // ─── Happy path ───────────────────────────────────────────────────

    @Test
    void patientCanCorrectOwnCaseAnswer() throws Exception {
        mockMvc.perform(post("/patient/cases/" + caseId + "/corrections")
                        .session(loginPatient())
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"answerOrder": 0, "correctedAnswer": "Dry cough for three weeks", "reason": "Misspoke earlier"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.correction.correctedAnswer").value("Dry cough for three weeks"))
                .andExpect(jsonPath("$.correction.originalAnswer").value("Persistent cough for two weeks"))
                .andExpect(jsonPath("$.correction.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.correction.correctedBy").value("patient"));
    }

    @Test
    void summarySurfacesOriginalAndCorrectionSideBySide() throws Exception {
        submitCorrection();
        mockMvc.perform(get("/patient/cases/" + caseId + "/summary").session(loginPatient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].answer").value("Persistent cough for two weeks"))
                .andExpect(jsonPath("$.answers[0].answerOrder").value(0))
                .andExpect(jsonPath("$.corrections[0].originalAnswer").value("Persistent cough for two weeks"))
                .andExpect(jsonPath("$.corrections[0].correctedAnswer").value("Dry cough for three weeks"))
                .andExpect(jsonPath("$.corrections[0].correctedAt").isNotEmpty());
    }

    @Test
    void resubmissionReplacesPendingCorrectionWithoutTouchingOriginal() throws Exception {
        submitCorrection();
        mockMvc.perform(post("/patient/cases/" + caseId + "/corrections")
                        .session(loginPatient())
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"answerOrder": 0, "correctedAnswer": "Dry cough for one month"}
                                """))
                .andExpect(status().isOk());

        // Exactly one current correction, and the original answer unchanged.
        mockMvc.perform(get("/patient/cases/" + caseId + "/summary").session(loginPatient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].answer").value("Persistent cough for two weeks"))
                .andExpect(jsonPath("$.corrections.length()").value(1))
                .andExpect(jsonPath("$.corrections[0].correctedAnswer").value("Dry cough for one month"));
    }

    // ─── Security / ownership boundary ────────────────────────────────

    @Test
    void patientCannotCorrectAnotherPatientsCase() throws Exception {
        // Register a second patient and give them their own case.
        String otherCase = createOtherPatientCase("Other patient headache");
        mockMvc.perform(post("/patient/cases/" + otherCase + "/corrections")
                        .session(loginPatient())
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"answerOrder": 0, "correctedAnswer": "Hijacked correction"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void patientCannotListAnotherPatientsCorrections() throws Exception {
        String otherCase = createOtherPatientCase("Other patient fatigue");
        mockMvc.perform(get("/patient/cases/" + otherCase + "/corrections")
                        .session(loginPatient()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousAccessIsDenied() throws Exception {
        mockMvc.perform(get("/patient/cases/" + caseId + "/corrections"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/patient/cases/" + caseId + "/corrections")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"answerOrder": 0, "correctedAnswer": "x"}
                                """))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void csrfIsRequired() throws Exception {
        mockMvc.perform(post("/patient/cases/" + caseId + "/corrections")
                        .session(loginPatient())
                        .contentType("application/json")
                        .content("""
                                {"answerOrder": 0, "correctedAnswer": "No CSRF token"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void physicianRoleCannotAccessPatientCorrectionEndpoints() throws Exception {
        mockMvc.perform(get("/patient/cases/" + caseId + "/corrections")
                        .session(loginPhysician()))
                .andExpect(status().isForbidden());
    }

    // ─── Clinical-integrity rules ─────────────────────────────────────

    @Test
    void emptyCorrectionRejected() throws Exception {
        mockMvc.perform(post("/patient/cases/" + caseId + "/corrections")
                        .session(loginPatient())
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"answerOrder": 0, "correctedAnswer": "   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void correctionEqualToOriginalRejected() throws Exception {
        mockMvc.perform(post("/patient/cases/" + caseId + "/corrections")
                        .session(loginPatient())
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"answerOrder": 0, "correctedAnswer": "Persistent cough for two weeks"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownAnswerOrderRejected() throws Exception {
        mockMvc.perform(post("/patient/cases/" + caseId + "/corrections")
                        .session(loginPatient())
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"answerOrder": 99, "correctedAnswer": "Does not exist"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditEventIsWrittenForCorrection() throws Exception {
        submitCorrection();
        // The admin audit trail must show the PATIENT_CORRECTION event with the
        // acting patient and the affected case.
        mockMvc.perform(get("/admin/home").session(login("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    assertThat(html).contains("PATIENT_CORRECTION");
                    assertThat(html).contains(caseId);
                });
    }

    @Test
    void physicianWorkspaceShowsOriginalAndCorrectionDistinctly() throws Exception {
        submitCorrection();

        mockMvc.perform(get("/physician/cases/" + caseId).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    // Original evidence remains visible verbatim…
                    assertThat(html).contains("Persistent cough for two weeks");
                    // …and the correction appears beside it with provenance.
                    assertThat(html).contains("Dry cough for three weeks");
                    assertThat(html).contains("Patient correction");
                    assertThat(html).contains("by patient");
                });
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private void submitCorrection() throws Exception {
        mockMvc.perform(post("/patient/cases/" + caseId + "/corrections")
                        .session(loginPatient())
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"answerOrder": 0, "correctedAnswer": "Dry cough for three weeks", "reason": "Misspoke earlier"}
                                """))
                .andExpect(status().isOk());
    }

    private String createOtherPatientCase(String answer) throws Exception {
        if (userRepository.findByUsername("correctionOther").isEmpty()) {
            mockMvc.perform(post("/register")
                            .with(csrf())
                            .param("username", "correctionOther")
                            .param("displayName", "Correction Other")
                            .param("password", "other123")
                            .param("confirmPassword", "other123"))
                    .andExpect(status().is3xxRedirection());
        }
        Long otherId = userRepository.findByUsername("correctionOther").orElseThrow().getId();

        ClinicalConversationResult result = new ClinicalConversationResult();
        result.record(new ClinicalAnswer("chief_complaint_symptom", "CHIEF_COMPLAINT",
                "SYMPTOM_PROBLEM", "Describe your problem", answer));
        CompletedCase completed = CompletedCase.withNewId(result, otherId);
        casePersistence.save(completed, otherId);
        return completed.id();
    }
}
