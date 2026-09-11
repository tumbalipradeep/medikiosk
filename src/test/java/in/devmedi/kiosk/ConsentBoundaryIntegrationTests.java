package in.devmedi.kiosk;

import in.devmedi.kiosk.module.audit.entity.AuditOutcome;
import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import in.devmedi.kiosk.module.audit.repository.AuditEventRepository;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.consent.entity.Consent;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import in.devmedi.kiosk.module.consent.repository.ConsentRepository;
import in.devmedi.kiosk.module.document.findings.repository.ClinicalDocumentFindingsRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentExtractionRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentRepository;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the consent boundary at the interoperability export layer. The
 * MediKiosk consent model ({@code Consent} entity, FHIR {@code Consent}
 * projection) governs in-clinic processing only and is <b>not</b> a grant of
 * authority to transmit PHI to ABDM. The M4.5 boundary deliberately never
 * transmits, so missing or partial consent cannot imply an unauthorized
 * transmission. Future ABDM adapters must implement their own consent
 * verification independently of this boundary.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsentBoundaryIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompletedCasePersistenceService persistence;

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private ClinicalDocumentFindingsRepository findingsRepository;

    @Autowired
    private ClinicalDocumentExtractionRepository extractionRepository;

    @Autowired
    private ClinicalDocumentRepository documentRepository;

    private Long patientUserId;

    @BeforeEach
    void clearPersistedData() {
        findingsRepository.findAll().forEach(findingsRepository::delete);
        extractionRepository.deleteAllInBatch();
        documentRepository.deleteAllInBatch();
        consentRepository.deleteAll();
        persistence.deleteAll();
        auditEventRepository.deleteAllInBatch();
        patientUserId = userRepository.findByUsername("patient").orElseThrow().getId();
    }

    private MockHttpSession loginPhysician() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", "physician")
                        .param("password", "physician123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private CompletedCase saveCase(int answerCount, Long ownerId) {
        ClinicalConversationResult result = new ClinicalConversationResult();
        for (int i = 0; i < answerCount; i++) {
            result.record(new ClinicalAnswer("question_" + i, "HISTORY_OF_PRESENT_ILLNESS",
                    "SOCRATES", "Question text " + i, "Answer " + i));
        }
        CompletedCase completed = ownerId == null
                ? CompletedCase.withNewId(result) : CompletedCase.withNewId(result, ownerId);
        if (ownerId == null) {
            persistence.save(completed);
        } else {
            persistence.save(completed, ownerId);
        }
        return completed;
    }

    private User patient() {
        return userRepository.findByUsername("patient").orElseThrow();
    }

    private String fhirUrl(String caseId) {
        return "/physician/cases/" + caseId + "/fhir";
    }

    @Test
    void missingConsentDoesNotBlockExport() throws Exception {
        CompletedCase completed = saveCase(1, patientUserId);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry[0].resource.resourceType").value("Patient"));

        assertThat(auditEventRepository.count()).isEqualTo(1);
        assertThat(auditEventRepository.findByCaseIdOrderByOccurredAtAsc(completed.id()).getFirst()
                .getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    void exportWithConsentStillWorks() throws Exception {
        User pat = patient();
        CompletedCase completed = saveCase(1, patientUserId);
        consentRepository.save(new Consent(pat, ConsentType.DATA_SHARING, "Share with ABDM"));
        consentRepository.save(new Consent(pat, ConsentType.DOCUMENT_PROCESSING, "Process docs"));

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry[0].resource.resourceType").value("Patient"));

        assertThat(auditEventRepository.findByCaseIdOrderByOccurredAtAsc(completed.id()).getFirst()
                .getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    void existingConsentFhirProjectionIsPreserved() throws Exception {
        User pat = patient();
        CompletedCase completed = saveCase(0, patientUserId);
        consentRepository.save(new Consent(pat, ConsentType.DOCUMENT_PROCESSING, "Process reports"));

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    List<String> types = JsonPath.read(result.getResponse().getContentAsString(),
                            "$.entry[*].resource.resourceType");
                    assertThat(types).contains("Consent");
                });
    }

    @Test
    void consentModelIsNotMutatedByExport() throws Exception {
        User pat = patient();
        CompletedCase completed = saveCase(1, patientUserId);
        consentRepository.save(new Consent(pat, ConsentType.DATA_SHARING, "Share data"));

        long before = consentRepository.count();
        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk());
        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk());

        assertThat(consentRepository.count()).isEqualTo(before);
    }
}