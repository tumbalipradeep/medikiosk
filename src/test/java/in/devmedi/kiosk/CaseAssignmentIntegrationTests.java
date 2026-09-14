package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.physician.assignment.AssignmentStatus;
import in.devmedi.kiosk.module.physician.assignment.CaseAssignmentRepository;
import in.devmedi.kiosk.module.physician.clinicalrecord.ConsultationRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Physician case queue and access control: a case with no active assignment
 * sits in the shared pool and is open to any physician, assigning it makes it
 * private to the assignee, releasing it returns it to the pool, and finalizing
 * the consultation completes the assignment. A second physician must not be
 * able to open or mutate a case locked to someone else.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CaseAssignmentIntegrationTests {

    private static final String SECOND_PHYSICIAN = "phys2";
    private static final String SECOND_PHYSICIAN_PASSWORD = "phys2pass123";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CompletedCaseReviewStore reviewStore;
    @Autowired private CompletedCasePersistenceService casePersistence;
    @Autowired private ConsultationRepository consultationRepository;
    @Autowired private CaseAssignmentRepository assignmentRepository;

    private Long patientId;

    @BeforeEach
    void setUp() {
        assignmentRepository.deleteAll();
        reviewStore.clear();
        consultationRepository.deleteAll();
        casePersistence.deleteAll();
        patientId = userRepository.findByUsername("patient").orElseThrow().getId();
        ensureSecondPhysician();
    }

    private void ensureSecondPhysician() {
        if (userRepository.findByUsername(SECOND_PHYSICIAN).isPresent()) {
            return;
        }
        userRepository.save(new User(
                SECOND_PHYSICIAN,
                passwordEncoder.encode(SECOND_PHYSICIAN_PASSWORD),
                "Dr Two",
                Role.PHYSICIAN));
    }

    private String saveCase(Long userId) {
        ClinicalConversationResult result = new ClinicalConversationResult();
        result.record(new ClinicalAnswer("chief_complaint_symptom", "CHIEF_COMPLAINT",
                "SYMPTOM_PROBLEM", "Describe your problem", "Routine follow-up review"));
        CompletedCase completed = new CompletedCase("case-" + java.util.UUID.randomUUID(), result, userId);
        casePersistence.save(completed, userId);
        return completed.id();
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

    private MockHttpSession physician() throws Exception {
        return login("physician", "physician123");
    }

    private MockHttpSession secondPhysician() throws Exception {
        return login(SECOND_PHYSICIAN, SECOND_PHYSICIAN_PASSWORD);
    }

    @Test
    void poolCaseIsAccessibleToAnyPhysician() throws Exception {
        String caseId = saveCase(patientId);

        mockMvc.perform(get("/physician/cases/{caseId}", caseId).session(physician()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/physician/cases/{caseId}", caseId).session(secondPhysician()))
                .andExpect(status().isOk());

        String dashboard = mockMvc.perform(get("/physician/home").session(physician()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(dashboard).contains("Pool (unassigned)");
    }

    @Test
    void assigningMakesCasePrivateToAssignee() throws Exception {
        String caseId = saveCase(patientId);
        MockHttpSession first = physician();

        mockMvc.perform(post("/physician/cases/{caseId}/assign", caseId).session(first).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/physician/cases/{caseId}", caseId).session(first))
                .andExpect(status().isOk());

        String otherDashboard = mockMvc.perform(get("/physician/home").session(secondPhysician()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(otherDashboard).contains("Locked");

        mockMvc.perform(get("/physician/cases/{caseId}", caseId).session(secondPhysician()))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignReleasesPreviousOwner() throws Exception {
        String caseId = saveCase(patientId);
        MockHttpSession first = physician();
        MockHttpSession second = secondPhysician();

        mockMvc.perform(post("/physician/cases/{caseId}/assign", caseId).session(first).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/physician/cases/{caseId}/assign", caseId).session(second).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/physician/cases/{caseId}", caseId).session(second))
                .andExpect(status().isOk());
        mockMvc.perform(get("/physician/cases/{caseId}", caseId).session(first))
                .andExpect(status().isForbidden());

        String secondDashboard = mockMvc.perform(get("/physician/home").session(second))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(secondDashboard).contains("Yours");
    }

    @Test
    void unassignReturnsCaseToPool() throws Exception {
        String caseId = saveCase(patientId);
        MockHttpSession first = physician();

        mockMvc.perform(post("/physician/cases/{caseId}/assign", caseId).session(first).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/physician/cases/{caseId}/unassign", caseId).session(first).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/physician/cases/{caseId}", caseId).session(secondPhysician()))
                .andExpect(status().isOk());

        String dashboard = mockMvc.perform(get("/physician/home").session(first))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(dashboard).contains("Pool (unassigned)");
    }

    @Test
    void finalizingConsultationCompletesAssignment() throws Exception {
        String caseId = saveCase(patientId);
        MockHttpSession first = physician();

        mockMvc.perform(post("/physician/cases/{caseId}/assign", caseId).session(first).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/physician/cases/{caseId}/consultation", caseId)
                        .session(first).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assessment\":\"Stable\",\"plan\":\"Observation\","
                                + "\"advice\":\"Hydration\",\"followUp\":\"One week\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/physician/cases/{caseId}/consultation/finalize", caseId)
                        .session(first).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FINALIZED")));

        assertThat(assignmentRepository.findByCompletedCase_CaseIdAndStatus(caseId, AssignmentStatus.ACTIVE))
                .isEmpty();
        assertThat(assignmentRepository.findByCompletedCase_CaseIdAndStatus(caseId, AssignmentStatus.COMPLETED))
                .isNotEmpty();

        mockMvc.perform(get("/physician/cases/{caseId}", caseId).session(secondPhysician()))
                .andExpect(status().isOk());
    }

    @Test
    void lockedCaseRejectsOtherPhysicianMutations() throws Exception {
        String caseId = saveCase(patientId);
        MockHttpSession first = physician();
        MockHttpSession second = secondPhysician();

        mockMvc.perform(post("/physician/cases/{caseId}/assign", caseId).session(first).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/physician/cases/{caseId}/record", caseId).session(second))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/physician/cases/{caseId}/timeline", caseId).session(second))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/physician/cases/{caseId}/medications", caseId).session(second))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/physician/cases/{caseId}/fhir", caseId).session(second))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCannotUsePhysicianQueue() throws Exception {
        String caseId = saveCase(patientId);
        MockHttpSession patient = login("patient", "patient123");

        mockMvc.perform(post("/physician/cases/{caseId}/assign", caseId).session(patient).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/physician/home").session(patient))
                .andExpect(status().isForbidden());
    }

    @Test
    void assigningUnknownCaseReturnsNotFound() throws Exception {
        mockMvc.perform(post("/physician/cases/{caseId}/assign", "case-missing")
                        .session(physician()).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
