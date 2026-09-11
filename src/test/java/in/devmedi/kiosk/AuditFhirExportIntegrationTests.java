package in.devmedi.kiosk;

import in.devmedi.kiosk.module.audit.entity.AuditEvent;
import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import in.devmedi.kiosk.module.audit.entity.AuditOutcome;
import in.devmedi.kiosk.module.audit.repository.AuditEventRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end audit-trail tests for the physician FHIR export endpoint.
 *
 * <p>Only controller-observable outcomes are recorded: a successful export or a
 * resolvable-case failure (404). Requests denied at the Spring Security filter
 * chain (403 for wrong role, 302 redirect for anonymous) are handled upstream
 * and deliberately not audited - the application never bypasses security
 * filters to create audit events. Every audit record contains only identifiers
 * and outcome; it never stores clinical answer contents, document binaries,
 * generated FHIR Bundles, passwords, tokens, or API keys.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditFhirExportIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompletedCasePersistenceService persistence;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private ClinicalDocumentFindingsRepository findingsRepository;

    @Autowired
    private ClinicalDocumentExtractionRepository extractionRepository;

    @Autowired
    private ClinicalDocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        return login("physician", "physician123");
    }

    private MockHttpSession loginPatient() throws Exception {
        return login("patient", "patient123");
    }

    private MockHttpSession loginAdmin() throws Exception {
        return login("admin", "admin123");
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
    void successfulExportCreatesExpectedAuditEvent() throws Exception {
        CompletedCase completed = saveCase(2, patientUserId);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk());

        List<AuditEvent> events = auditEventRepository.findByCaseIdOrderByOccurredAtAsc(completed.id());
        assertThat(events).hasSize(1);

        AuditEvent event = events.getFirst();
        assertThat(event.getId()).isNotNull();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.FHIR_EXPORT);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getActorUsername()).isEqualTo("physician");
        assertThat(event.getActorRole()).isEqualTo("PHYSICIAN");
        assertThat(event.getCaseId()).isEqualTo(completed.id());
        assertThat(event.getOperation()).isEqualTo("EXPORT");
        assertThat(event.getResourceType()).isEqualTo("Bundle");
        assertThat(event.getFailureReason()).isNull();
        assertThat(event.getRequestPath()).isEqualTo(fhirUrl(completed.id()));
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    void repeatedExportsGenerateSeparateAuditEvents() throws Exception {
        CompletedCase completed = saveCase(1, patientUserId);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk());
        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk());

        List<AuditEvent> events = auditEventRepository.findByCaseIdOrderByOccurredAtAsc(completed.id());
        assertThat(events).hasSize(2);
        assertThat(events).allSatisfy(e -> {
            assertThat(e.getEventType()).isEqualTo(AuditEventType.FHIR_EXPORT);
            assertThat(e.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
            assertThat(e.getActorUsername()).isEqualTo("physician");
        });
    }

    @Test
    void patientAttemptCreatesNoAuditEvent() throws Exception {
        CompletedCase completed = saveCase(1, patientUserId);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPatient()))
                .andExpect(status().isForbidden());

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void adminAttemptCreatesNoAuditEvent() throws Exception {
        CompletedCase completed = saveCase(1, patientUserId);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginAdmin()))
                .andExpect(status().isForbidden());

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void anonymousAttemptCreatesNoAuditEvent() throws Exception {
        mockMvc.perform(get(fhirUrl("any-case-id")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void nonexistentCaseCreatesFailureAuditEvent() throws Exception {
        mockMvc.perform(get(fhirUrl("does-not-exist")).session(loginPhysician()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Case not found"));

        List<AuditEvent> events = auditEventRepository.findByCaseIdOrderByOccurredAtAsc("does-not-exist");
        assertThat(events).hasSize(1);

        AuditEvent event = events.getFirst();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.FHIR_EXPORT);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(event.getFailureReason()).isEqualTo("CASE_NOT_FOUND");
        assertThat(event.getCaseId()).isEqualTo("does-not-exist");
        assertThat(event.getActorUsername()).isEqualTo("physician");
        assertThat(event.getActorRole()).isEqualTo("PHYSICIAN");
        assertThat(event.getRequestPath()).isEqualTo(fhirUrl("does-not-exist"));
    }

    @Test
    void ownerlessCaseExportsSuccessWithCorrectActor() throws Exception {
        CompletedCase completed = saveCase(1, null);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk());

        List<AuditEvent> events = auditEventRepository.findByCaseIdOrderByOccurredAtAsc(completed.id());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(events.getFirst().getCaseId()).isEqualTo(completed.id());
        assertThat(events.getFirst().getActorUsername()).isEqualTo("physician");
    }

    @Test
    void auditRecordContainsOnlyExpectedIdentifiersNoClinicalData() throws Exception {
        CompletedCase completed = saveCase(2, patientUserId);

        mockMvc.perform(get(fhirUrl(completed.id())).session(loginPhysician()))
                .andExpect(status().isOk());

        AuditEvent event = auditEventRepository.findByCaseIdOrderByOccurredAtAsc(completed.id()).getFirst();

        // Audit fields must not contain clinical answer text or bundle content
        String eventAsString = event.toString();
        assertThat(eventAsString).doesNotContain("Answer 0", "Answer 1", "Question text 0");
        assertThat(eventAsString).doesNotContain("\"entry\"", "base64", "document/pdf");

        // The audit_events table must contain only the expected structural columns
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='AUDIT_EVENTS' ORDER BY ORDINAL_POSITION",
                String.class);
        assertThat(columns).containsExactly(
                "ID", "EVENT_TYPE", "OCCURRED_AT", "ACTOR_USERNAME", "ACTOR_ROLE",
                "CASE_ID", "OPERATION", "RESOURCE_TYPE", "OUTCOME", "FAILURE_REASON", "REQUEST_PATH");
    }

    @Test
    void auditEventRepositoryPersistenceRoundTrip() {
        AuditEvent event = new AuditEvent(
                AuditEventType.FHIR_EXPORT, java.time.Instant.now(),
                "physician", "PHYSICIAN", "case-123",
                "EXPORT", "Bundle", AuditOutcome.SUCCESS, null,
                "/physician/cases/case-123/fhir");

        AuditEvent saved = auditEventRepository.save(event);
        auditEventRepository.flush();

        AuditEvent loaded = auditEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getEventType()).isEqualTo(AuditEventType.FHIR_EXPORT);
        assertThat(loaded.getCaseId()).isEqualTo("case-123");
        assertThat(loaded.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(loaded.getRequestPath()).isEqualTo("/physician/cases/case-123/fhir");
    }
}