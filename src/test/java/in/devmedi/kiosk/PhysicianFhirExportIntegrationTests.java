package in.devmedi.kiosk;

import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import in.devmedi.kiosk.module.audit.entity.AuditOutcome;
import in.devmedi.kiosk.module.audit.repository.AuditEventRepository;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.consent.entity.Consent;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import in.devmedi.kiosk.module.consent.repository.ConsentRepository;
import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.extraction.ExtractionErrorCategory;
import in.devmedi.kiosk.module.document.extraction.ExtractionOutcome;
import in.devmedi.kiosk.module.document.extraction.ExtractionResult;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindings;
import in.devmedi.kiosk.module.document.findings.model.AbnormalityStatus;
import in.devmedi.kiosk.module.document.findings.model.EncounterMetadata;
import in.devmedi.kiosk.module.document.findings.model.InteractionAnalysisStatus;
import in.devmedi.kiosk.module.document.findings.model.LabResult;
import in.devmedi.kiosk.module.document.findings.model.PatientIdentifiers;
import in.devmedi.kiosk.module.document.findings.model.StructuredFindings;
import in.devmedi.kiosk.module.document.findings.model.VitalSign;
import in.devmedi.kiosk.module.document.findings.model.VitalType;
import in.devmedi.kiosk.module.document.findings.repository.ClinicalDocumentFindingsRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentExtractionRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentRepository;
import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.interop.FhirExportContract;
import in.devmedi.kiosk.module.fhir.mapping.FhirDateTimes;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseAnswerRepository;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the physician FHIR export endpoint
 * ({@code GET /physician/cases/{caseId}/fhir}).
 *
 * <p>Authorization mirrors the existing MediKiosk physician workflow: the
 * {@code /physician/**} route requires {@code ROLE_PHYSICIAN} (centralized in
 * {@code SecurityConfig}), patients/admins/anonymous users are denied, and an
 * unresolvable case maps to 404. MediKiosk has no per-physician case
 * assignment model, so - consistent with the timeline/findings/extraction
 * endpoints - any authenticated physician may read any persisted case by id;
 * this endpoint does not widen that access surface.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PhysicianFhirExportIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompletedCasePersistenceService persistence;

    @Autowired
    private CompletedCaseRepository completedCaseRepository;

    @Autowired
    private CompletedCaseAnswerRepository answerRepository;

    @Autowired
    private ClinicalDocumentRepository documentRepository;

    @Autowired
    private ClinicalDocumentExtractionRepository extractionRepository;

    @Autowired
    private ClinicalDocumentFindingsRepository findingsRepository;

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

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

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession loginPhysician() throws Exception {
        return login("physician", "physician123");
    }

    private MockHttpSession loginPatient() throws Exception {
        return login("patient", "patient123");
    }

    private MockHttpSession loginAdmin() throws Exception {
        return login("admin", "admin123");
    }

    private User patient() {
        return userRepository.findByUsername("patient").orElseThrow();
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

    private CompletedCaseEntity caseEntity(CompletedCase completed) {
        return completedCaseRepository.findByCaseId(completed.id()).orElseThrow();
    }

    private void saveDocumentWithFindings(CompletedCaseEntity caseEntity, User patient) {
        ClinicalDocument document = ClinicalDocument.create("doc-fhir-export", caseEntity, patient,
                "lab-report.pdf", "stored.pdf", "application/pdf", 2048);
        documentRepository.save(document);

        ClinicalDocumentExtraction extraction = ClinicalDocumentExtraction.create(document,
                new ExtractionOutcome(ExtractionStatus.EXTRACTED, ExtractionErrorCategory.NONE, null,
                        "report text", 1, List.of(new ExtractionResult.PageText(1, "report text"))));
        extractionRepository.save(extraction);

        StructuredFindings structured = new StructuredFindings(
                new PatientIdentifiers("Ravi Kumar", null, "Male", "MRN123"),
                new EncounterMetadata(null, null, null, null, "Lab Report"),
                List.of(new VitalSign(VitalType.BLOOD_PRESSURE, "128/82", "mmHg",
                        "BP: 128/82 mmHg", 0)),
                List.of(new LabResult("Hemoglobin", "14.2 g/dL", "14.2", "g/dL",
                        "13.0 - 17.0", null, "Hb: 14.2 g/dL", 0, AbnormalityStatus.NORMAL)),
                List.of(), InteractionAnalysisStatus.NOT_AVAILABLE);
        findingsRepository.save(ClinicalDocumentFindings.create(extraction, structured));
    }

    private String fhirUrl(String caseId) {
        return "/physician/cases/" + caseId + "/fhir";
    }

    @Test
    void authorizedPhysicianGets200WithFhirBundleJson() throws Exception {
        User patient = patient();
        CompletedCase completed = saveCase(2, patient.getId());
        saveDocumentWithFindings(caseEntity(completed), patient);
        consentRepository.save(new Consent(patient, ConsentType.DOCUMENT_PROCESSING, "Process my reports"));

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("application/fhir+json")))
                .andExpect(header().string(FhirExportContract.FHIR_VERSION_HEADER, FhirExportContract.FHIR_VERSION))
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.type").value("collection"))
                .andExpect(jsonPath("$.entry").isArray())
                .andExpect(jsonPath("$.entry.length()").value(8))
                .andExpect(jsonPath("$.entry[0].resource.resourceType").value("Patient"))
                .andExpect(jsonPath("$.entry[1].resource.resourceType").value("Encounter"))
                .andExpect(jsonPath("$.entry[2].resource.resourceType").value("Observation"))
                .andExpect(jsonPath("$.entry[3].resource.resourceType").value("Observation"))
                .andExpect(jsonPath("$.entry[4].resource.resourceType").value("DocumentReference"))
                .andExpect(jsonPath("$.entry[5].resource.resourceType").value("Observation"))
                .andExpect(jsonPath("$.entry[6].resource.resourceType").value("Observation"))
                .andExpect(jsonPath("$.entry[7].resource.resourceType").value("Consent"));
    }

    @Test
    void patientIsDenied() throws Exception {
        CompletedCase completed = saveCase(1, patientUserId);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPatient()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminIsDenied() throws Exception {
        CompletedCase completed = saveCase(1, patientUserId);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get(fhirUrl("any-case-id")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void nonexistentCaseReturns404() throws Exception {
        mockMvc.perform(get(fhirUrl("does-not-exist")).session(loginPhysician()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Case not found"));
    }

    @Test
    void bundleContainsExpectedClinicalContent() throws Exception {
        User patient = patient();
        CompletedCase completed = saveCase(2, patient.getId());
        saveDocumentWithFindings(caseEntity(completed), patient);
        consentRepository.save(new Consent(patient, ConsentType.DATA_SHARING, "Share with ABDM"));

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry[0].resource.resourceType").value("Patient"))
                .andExpect(jsonPath("$.entry[1].resource.resourceType").value("Encounter"))
                .andExpect(jsonPath("$.entry[1].resource.status").value("finished"))
                .andExpect(jsonPath("$.entry[2].resource.status").value("final"))
                .andExpect(jsonPath("$.entry[2].resource.valueString").value("Answer 0"))
                .andExpect(jsonPath("$.entry[3].resource.valueString").value("Answer 1"))
                .andExpect(jsonPath("$.entry[4].resource.resourceType").value("DocumentReference"))
                .andExpect(jsonPath("$.entry[4].resource.status").value("current"))
                .andExpect(jsonPath("$.entry[5].resource.code.coding[0].system").value("urn:medikiosk:vital-type"))
                .andExpect(jsonPath("$.entry[5].resource.valueString").value("128/82"))
                .andExpect(jsonPath("$.entry[6].resource.code.coding[0].system").value("urn:medikiosk:lab-test"))
                .andExpect(jsonPath("$.entry[6].resource.valueQuantity.value").value(14.2))
                .andExpect(jsonPath("$.entry[7].resource.resourceType").value("Consent"));
    }

    @Test
    void referencesResolveAndDeterministicIdsArePreserved() throws Exception {
        User patient = patient();
        CompletedCase completed = saveCase(1, patient.getId());

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(FhirIds.logicalId("Bundle", completed.id())))
                .andExpect(jsonPath("$.entry[0].resource.id").value(FhirIds.patient(patient.getUsername())))
                .andExpect(jsonPath("$.entry[1].resource.id").value(FhirIds.encounter(completed.id())))
                .andExpect(jsonPath("$.entry[2].resource.id").value(FhirIds.answer(completed.id(), 0)))
                .andExpect(result -> {
                    List<String> urls = JsonPath.read(result.getResponse().getContentAsString(),
                            "$.entry[*].fullUrl");
                    assertThat(urls).hasSize(3).allSatisfy(url -> assertThat(url).startsWith("urn:uuid:"));
                });
    }

    @Test
    void noBinaryDocumentContentIsExposed() throws Exception {
        User patient = patient();
        CompletedCase completed = saveCase(1, patient.getId());
        saveDocumentWithFindings(caseEntity(completed), patient);

        String body = mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("\"data\"", "base64");
    }

    @Test
    void ownerlessCaseExportsEncounterAndAnswersOnly() throws Exception {
        CompletedCase completed = saveCase(1, null);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.length()").value(2))
                .andExpect(jsonPath("$.entry[0].resource.resourceType").value("Encounter"))
                .andExpect(jsonPath("$.entry[1].resource.resourceType").value("Observation"));
    }

    @Test
    void caseWithoutDocumentsExportsNoDocumentReference() throws Exception {
        CompletedCase completed = saveCase(1, patientUserId);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    List<String> types = JsonPath.read(result.getResponse().getContentAsString(),
                            "$.entry[*].resource.resourceType");
                    assertThat(types).containsExactly("Patient", "Encounter", "Observation");
                });
    }

    @Test
    void metadataOnlyDocumentExportsDocumentReferenceWithoutObservations() throws Exception {
        User patient = patient();
        CompletedCase completed = saveCase(0, patient.getId());
        documentRepository.save(ClinicalDocument.create("doc-meta-only", caseEntity(completed), patient,
                "scan.pdf", "stored.pdf", "image/jpeg", 4096));

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    List<String> types = JsonPath.read(result.getResponse().getContentAsString(),
                            "$.entry[*].resource.resourceType");
                    assertThat(types).containsExactly("Patient", "Encounter", "DocumentReference");
                });
    }

    @Test
    void missingVitalsLabsAndConsentsAreOmitted() throws Exception {
        User patient = patient();
        CompletedCase completed = saveCase(0, patient.getId());
        ClinicalDocument document = ClinicalDocument.create("doc-no-vitals", caseEntity(completed), patient,
                "report.pdf", "stored.pdf", "application/pdf", 1024);
        documentRepository.save(document);
        ClinicalDocumentExtraction extraction = ClinicalDocumentExtraction.create(document,
                new ExtractionOutcome(ExtractionStatus.EXTRACTED, ExtractionErrorCategory.NONE, null,
                        "report text", 1, List.of(new ExtractionResult.PageText(1, "report text"))));
        extractionRepository.save(extraction);
        findingsRepository.save(ClinicalDocumentFindings.create(extraction, new StructuredFindings(
                new PatientIdentifiers("Ravi Kumar", null, "Male", "MRN123"),
                new EncounterMetadata(null, null, null, null, "Report"),
                List.of(), List.of(), List.of(), InteractionAnalysisStatus.NOT_AVAILABLE)));

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    List<String> types = JsonPath.read(result.getResponse().getContentAsString(),
                            "$.entry[*].resource.resourceType");
                    assertThat(types).containsExactly("Patient", "Encounter", "DocumentReference");
                });
    }

    @Test
    void repeatedExportsReturnByteIdenticalJson() throws Exception {
        User patient = patient();
        CompletedCase completed = saveCase(2, patient.getId());
        saveDocumentWithFindings(caseEntity(completed), patient);
        consentRepository.save(new Consent(patient, ConsentType.DOCUMENT_PROCESSING, "Process my reports"));

        String first = mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void timestampIsDerivedFromPersistedCaseCreationTime() throws Exception {
        CompletedCase completed = saveCase(1, patientUserId);
        Instant createdAt = completedCaseRepository.findByCaseId(completed.id()).orElseThrow().getCreatedAt();

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String timestamp = JsonPath.read(result.getResponse().getContentAsString(),
                            "$.timestamp");
                    assertThat(timestamp).isEqualTo(FhirDateTimes.instant(createdAt));
                    assertThat(timestamp).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$");
                });
    }

    /**
     * The export request does not mutate clinical tables. It now writes one
     * audit event per call to {@code audit_events}; this is an expected
     * security-trail side effect, not clinical data.
     */
    @Test
    void exportDoesNotWriteClinicalData() throws Exception {
        User patient = patient();
        CompletedCase completed = saveCase(1, patient.getId());
        saveDocumentWithFindings(caseEntity(completed), patient);
        consentRepository.save(new Consent(patient, ConsentType.DOCUMENT_PROCESSING, "Process my reports"));

        long casesBefore = completedCaseRepository.count();
        long answersBefore = answerRepository.count();
        long documentsBefore = documentRepository.count();
        long extractionsBefore = extractionRepository.count();
        long findingsBefore = findingsRepository.count();
        long consentsBefore = consentRepository.count();

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk());
        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk());

        assertThat(completedCaseRepository.count()).isEqualTo(casesBefore);
        assertThat(answerRepository.count()).isEqualTo(answersBefore);
        assertThat(documentRepository.count()).isEqualTo(documentsBefore);
        assertThat(extractionRepository.count()).isEqualTo(extractionsBefore);
        assertThat(findingsRepository.count()).isEqualTo(findingsBefore);
        assertThat(consentRepository.count()).isEqualTo(consentsBefore);
        assertThat(auditEventRepository.count()).isEqualTo(2);
    }

    @Test
    void existingPhysicianTimelineWorkflowIsUnaffected() throws Exception {
        CompletedCase completed = saveCase(1, patientUserId);

        mockMvc.perform(get("/physician/cases/" + completed.id() + "/timeline").session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isArray());
    }
}