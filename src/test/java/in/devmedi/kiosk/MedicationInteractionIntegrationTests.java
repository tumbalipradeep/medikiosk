package in.devmedi.kiosk;

import com.jayway.jsonpath.JsonPath;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryCategory;
import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryItemRepository;
import in.devmedi.kiosk.module.clinical.history.ClinicalHistoryService;
import in.devmedi.kiosk.module.clinical.provenance.ClinicalProvenance;
import in.devmedi.kiosk.module.medication.repository.CaseMedicationRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Medication interaction screening flow: sourced medicines come from the
 * patient's clinical history, the physician overlay adds/suppresses/restores
 * medicines, interactions are screened from the seeded rule data, and the
 * patient role cannot reach the physician endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MedicationInteractionIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CompletedCaseReviewStore reviewStore;
    @Autowired private CompletedCasePersistenceService casePersistence;
    @Autowired private ClinicalHistoryService historyService;
    @Autowired private ClinicalHistoryItemRepository historyRepository;
    @Autowired private CaseMedicationRepository caseMedicationRepository;

    private Long patientId;

    @BeforeEach
    void setUp() {
        reviewStore.clear();
        casePersistence.deleteAll();
        caseMedicationRepository.deleteAll();
        patientId = userRepository.findByUsername("patient").orElseThrow().getId();
        historyRepository.deleteAll(historyRepository.findByPatientIdOrderByCategoryAscConceptKeyAsc(patientId));
    }

    private String saveCase(Long userId) {
        ClinicalConversationResult result = new ClinicalConversationResult();
        result.record(new ClinicalAnswer("chief_complaint_symptom", "HISTORY",
                "TYPE", "Canonical question", "Warfarin dosage check"));
        CompletedCase completed = new CompletedCase("case-" + java.util.UUID.randomUUID(), result, userId);
        casePersistence.save(completed, userId);
        return completed.id();
    }

    private void seedHistoryMedications(String value) {
        historyService.record(patientId, ClinicalHistoryCategory.MEDICATIONS,
                "current_medications", "Current medicines", value, null,
                ClinicalProvenance.PATIENT_REPORTED, "test");
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

    @Test
    void reportListsSourcedHistoryMedications() throws Exception {
        String caseId = saveCase(patientId);
        seedHistoryMedications("Metformin 500 mg twice daily, Tab. Aspirin 75 mg once daily");

        MockHttpSession session = physician();
        String body = mockMvc.perform(get("/physician/cases/{caseId}/medications", caseId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(body, "$.medications[*].displayName");
        assertThat(names).contains("Metformin", "Aspirin");
        assertThat(JsonPath.<List<?>>read(body, "$.interactions")).isEmpty();
    }

    @Test
    void addingWarfarinScreensInteractionWithAspirin() throws Exception {
        String caseId = saveCase(patientId);
        seedHistoryMedications("Aspirin 75 mg once daily");

        MockHttpSession session = physician();
        String body = mockMvc.perform(post("/physician/cases/{caseId}/medications", caseId)
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Warfarin\",\"dose\":\"5 mg\",\"frequency\":\"once daily\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(body, "$.medications[*].displayName");
        assertThat(names).contains("Warfarin", "Aspirin");
        List<String> severities = JsonPath.read(body, "$.interactions[*].severity");
        assertThat(severities).contains("MAJOR");
        assertThat(JsonPath.<String>read(body, "$.overallSeverity")).isEqualTo("MAJOR");
    }

    @Test
    void suppressAndRestoreSourcedMedicine() throws Exception {
        String caseId = saveCase(patientId);
        seedHistoryMedications("Warfarin 3 mg nightly, Aspirin 75 mg daily");

        MockHttpSession session = physician();
        String initial = mockMvc.perform(get("/physician/cases/{caseId}/medications", caseId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<?>>read(initial, "$.interactions")).isNotEmpty();

        String suppressed = mockMvc.perform(post("/physician/cases/{caseId}/medications/{name}/suppress",
                        caseId, "Aspirin")
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(suppressed, "$.medications[*].displayName"))
                .doesNotContain("Aspirin");
        assertThat(JsonPath.<List<?>>read(suppressed, "$.interactions")).isEmpty();

        String restored = mockMvc.perform(post("/physician/cases/{caseId}/medications/{name}/restore",
                        caseId, "Aspirin")
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(restored, "$.medications[*].displayName"))
                .contains("Aspirin");
        assertThat(JsonPath.<List<?>>read(restored, "$.interactions")).isNotEmpty();
    }

    @Test
    void patientRoleCannotReadPhysicianScreening() throws Exception {
        String caseId = saveCase(patientId);
        MockHttpSession session = login("patient", "patient123");
        mockMvc.perform(get("/physician/cases/{caseId}/medications", caseId).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownCaseReturnsNotFound() throws Exception {
        MockHttpSession session = physician();
        mockMvc.perform(get("/physician/cases/{caseId}/medications", "case-missing").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingBlankMedicineNameRejected() throws Exception {
        String caseId = saveCase(patientId);
        MockHttpSession session = physician();
        mockMvc.perform(post("/physician/cases/{caseId}/medications", caseId)
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}