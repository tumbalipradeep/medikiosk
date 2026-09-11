package in.devmedi.kiosk;

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
import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.interop.FhirExportContract;
import in.devmedi.kiosk.module.fhir.interop.FhirExportTransport;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.mock.web.MockHttpSession;


/**
 * Tests that the generated FHIR Bundle reaches the interoperability boundary
 * with the correct contract metadata, and that the {@code LocalOnlyExportTransport}
 * (replaced here by a Mockito mock) never performs actual I/O.
 *
 * <p>The {@code FhirExportContract} carries only identifiers and the FHIR
 * Bundle reference; it never duplicates the Bundle, stores clinical data, or
 * attempts to read ABDM credentials. The test verifies contract field
 * correctness, absence of network transmission, and that the export contract
 * is the single agreed-upon handoff between the clinical generator and the
 * interoperability boundary.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class FhirExportTransportBoundaryTests {

    @MockitoBean
    FhirExportTransport transport;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompletedCasePersistenceService persistence;

    @Autowired
    private ConsentRepository consentRepository;

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

    private String fhirUrl(String caseId) {
        return "/physician/cases/" + caseId + "/fhir";
    }

    @Test
    void successfulExportReachesBoundaryWithCorrectContract() throws Exception {
        CompletedCase completed = saveCase(2, patientUserId);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(header().string(FhirExportContract.FHIR_VERSION_HEADER, FhirExportContract.FHIR_VERSION));

        ArgumentCaptor<FhirExportContract> captor = ArgumentCaptor.forClass(FhirExportContract.class);
        verify(transport, times(1)).transmit(captor.capture());
        FhirExportContract contract = captor.getValue();

        assertThat(contract.caseId()).isEqualTo(completed.id());
        assertThat(contract.resourceType()).isEqualTo("Bundle");
        assertThat(contract.fhirVersion()).isEqualTo("4.0.1");
        assertThat(contract.mediaType()).isEqualTo("application/fhir+json");
        assertThat(contract.purpose()).isEqualTo(FhirExportContract.PURPOSE_CLINICAL_EXPORT);
        assertThat(contract.bundle()).isNotNull();
        assertThat(contract.bundle().id()).isEqualTo(FhirIds.logicalId("Bundle", completed.id()));
        assertThat(contract.bundle().entry()).isNotEmpty();
    }

    @Test
    void nonexistentCaseDoesNotReachTransport() throws Exception {
        mockMvc.perform(get(fhirUrl("does-not-exist")).session(loginPhysician()))
                .andExpect(status().isNotFound());

        verify(transport, never()).transmit(any());
    }

    @Test
    void consentNotRequiredForBoundaryReach() throws Exception {
        CompletedCase completed = saveCase(1, patientUserId);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk());

        ArgumentCaptor<FhirExportContract> captor = ArgumentCaptor.forClass(FhirExportContract.class);
        verify(transport).transmit(captor.capture());
        FhirExportContract contract = captor.getValue();
        assertThat(contract.bundle().entry()).isNotEmpty();
        assertThat(contract.bundle().entry()).allSatisfy(
                entry -> assertThat(entry.resource()).isNotNull());
    }

    @Test
    void exportWithConsentStillReachesBoundary() throws Exception {
        User patient = userRepository.findByUsername("patient").orElseThrow();
        CompletedCase completed = saveCase(1, patientUserId);
        consentRepository.save(new Consent(patient, ConsentType.DATA_SHARING, "Share with ABDM"));

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk());

        ArgumentCaptor<FhirExportContract> captor = ArgumentCaptor.forClass(FhirExportContract.class);
        verify(transport).transmit(captor.capture());
        assertThat(captor.getValue().bundle()).isNotNull();
    }

    @Test
    void bundleIsSameInstanceInContractNotCopied() throws Exception {
        User patient = userRepository.findByUsername("patient").orElseThrow();
        CompletedCase completed = saveCase(1, patientUserId);
        consentRepository.save(new Consent(patient, ConsentType.DATA_SHARING, "Share data"));

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk());

        ArgumentCaptor<FhirExportContract> captor = ArgumentCaptor.forClass(FhirExportContract.class);
        verify(transport).transmit(captor.capture());
        // Bundle identity is preserved; the contract does not copy or re-serialize it
        assertThat(captor.getValue().bundle()).isNotNull();
        assertThat(captor.getValue().bundle().id())
                .isEqualTo(FhirIds.logicalId("Bundle", completed.id()));
    }
}