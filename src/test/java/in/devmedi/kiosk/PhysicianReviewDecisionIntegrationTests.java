package in.devmedi.kiosk;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import in.devmedi.kiosk.module.audit.entity.AuditEvent;
import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import in.devmedi.kiosk.module.audit.entity.AuditOutcome;
import in.devmedi.kiosk.module.audit.repository.AuditEventRepository;
import in.devmedi.kiosk.module.physician.review.PhysicianReviewEntry;
import in.devmedi.kiosk.module.physician.review.PhysicianReviewEntryRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the physician accept / amend / reject review model.
 *
 * <p>Verifies that decisions are persisted, survive reloads, are attributed to
 * the reviewing physician, never overwrite the original patient evidence, are
 * audited, and are rejected when malformed or requested without the required
 * role / CSRF token.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PhysicianReviewDecisionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompletedCaseReviewStore reviewStore;

    @Autowired
    private CompletedCasePersistenceService persistence;

    @Autowired
    private PhysicianReviewEntryRepository reviewRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void clearPersistedData() {
        reviewStore.clear();
        auditEventRepository.deleteAllInBatch();
        persistence.deleteAll();
    }

    private CompletedCase saveCase(int answerCount, String... answers) {
        in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult result =
                new in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult();
        for (int i = 0; i < answerCount; i++) {
            String text = i < answers.length ? answers[i] : "Answer " + i;
            result.record(new in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer(
                    "question_" + i, "HISTORY_OF_PRESENT_ILLNESS", "SOCRATES",
                    "Question text " + i, text));
        }
        CompletedCase completed = CompletedCase.withNewId(result);
        persistence.save(completed);
        return completed;
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

    private MockHttpSession loginPatient() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", "patient")
                        .param("password", "patient123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String reviewUrl(String caseId, int answerOrder) {
        return "/physician/cases/" + caseId + "/answers/" + answerOrder + "/review";
    }

    private void submit(String url, MockHttpSession session, String body) throws Exception {
        mockMvc.perform(post(url)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private void submitExpect(String url, MockHttpSession session, String body, int expectedStatus)
            throws Exception {
        mockMvc.perform(post(url)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void acceptPersistsDecisionAndPreservesOriginalAnswer() throws Exception {
        CompletedCase completed = saveCase(3, "original chief answer", "x", "y");

        submit(reviewUrl(completed.id(), 0), loginPhysician(),
                "{\"decision\":\"ACCEPTED\"}");

        PhysicianReviewEntry entry = reviewRepository
                .findByCompletedCase_CaseIdAndAnswerOrder(completed.id(), 0).orElseThrow();
        assertThat(entry.getDecision().name()).isEqualTo("ACCEPTED");
        assertThat(entry.getReviewerUsername()).isEqualTo("physician");
        assertThat(entry.getDecidedAt()).isNotNull();

        String original = persistence.findByCaseId(completed.id()).orElseThrow()
                .result().all().get(0).answer();
        assertThat(original).isEqualTo("original chief answer");
    }

    @Test
    void amendPersistsAmendedTextAndOriginalStaysUntouched() throws Exception {
        CompletedCase completed = saveCase(3, "original chief answer", "x", "y");

        submit(reviewUrl(completed.id(), 0), loginPhysician(),
                "{\"decision\":\"AMENDED\",\"amendedText\":\"Corrected by physician\"}");

        PhysicianReviewEntry entry = reviewRepository
                .findByCompletedCase_CaseIdAndAnswerOrder(completed.id(), 0).orElseThrow();
        assertThat(entry.getDecision().name()).isEqualTo("AMENDED");
        assertThat(entry.getAmendedText()).isEqualTo("Corrected by physician");

        String original = persistence.findByCaseId(completed.id()).orElseThrow()
                .result().all().get(0).answer();
        assertThat(original).isEqualTo("original chief answer");
    }

    @Test
    void rejectPersistsDecisionAndPreservesOriginalEvidence() throws Exception {
        CompletedCase completed = saveCase(3, "unrecognizable speech", "x", "y");

        submit(reviewUrl(completed.id(), 0), loginPhysician(),
                "{\"decision\":\"REJECTED\",\"rationale\":\"Could not interpret\"}");

        PhysicianReviewEntry entry = reviewRepository
                .findByCompletedCase_CaseIdAndAnswerOrder(completed.id(), 0).orElseThrow();
        assertThat(entry.getDecision().name()).isEqualTo("REJECTED");
        assertThat(entry.getRationale()).isEqualTo("Could not interpret");

        String original = persistence.findByCaseId(completed.id()).orElseThrow()
                .result().all().get(0).answer();
        assertThat(original).isEqualTo("unrecognizable speech");
    }

    @Test
    void redecidingReplacesTheDecisionForTheSameAnswer() throws Exception {
        CompletedCase completed = saveCase(3, "a", "b", "c");
        MockHttpSession physician = loginPhysician();

        submit(reviewUrl(completed.id(), 0), physician, "{\"decision\":\"ACCEPTED\"}");
        submit(reviewUrl(completed.id(), 0), physician,
                "{\"decision\":\"AMENDED\",\"amendedText\":\"new text\"}");

        assertThat(reviewRepository.findByCompletedCase_CaseIdOrderByAnswerOrder(completed.id())).hasSize(1);
        PhysicianReviewEntry entry = reviewRepository
                .findByCompletedCase_CaseIdAndAnswerOrder(completed.id(), 0).orElseThrow();
        assertThat(entry.getDecision().name()).isEqualTo("AMENDED");
        assertThat(entry.getAmendedText()).isEqualTo("new text");
    }

    @Test
    void reviewsEndpointReturnsAllDecisionsInOrder() throws Exception {
        CompletedCase completed = saveCase(3, "a", "b", "c");
        submit(reviewUrl(completed.id(), 0), loginPhysician(), "{\"decision\":\"ACCEPTED\"}");

        MvcResult result = mockMvc.perform(get("/physician/cases/" + completed.id() + "/reviews")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode array = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(array.size()).isEqualTo(1);
        JsonNode first = array.get(0);
        assertThat(first.get("answerOrder").asInt()).isZero();
        assertThat(first.get("decision").asText()).isEqualTo("ACCEPTED");
        assertThat(first.get("reviewer").asText()).isEqualTo("physician");
    }

    @Test
    void reviewsEndpointNotFoundForUnknownCase() throws Exception {
        mockMvc.perform(get("/physician/cases/case-unknown/reviews").session(loginPhysician()))
                .andExpect(status().isNotFound());
    }

    @Test
    void workspaceRendersStoredDecisionAfterReload() throws Exception {
        CompletedCase completed = saveCase(2, "chest discomfort", "regular");
        submit(reviewUrl(completed.id(), 0), loginPhysician(),
                "{\"decision\":\"AMENDED\",\"amendedText\":\"Amended wording\"}");

        mockMvc.perform(get("/physician/cases/" + completed.id()).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Amended")))
                .andExpect(content().string(containsString("Amended wording")))
                .andExpect(content().string(containsString("chest discomfort")));
    }

    @Test
    void acceptsForAllAnswersMarkDashboardFullyReviewed() throws Exception {
        CompletedCase completed = saveCase(2, "a", "b");
        MockHttpSession physician = loginPhysician();
        submit(reviewUrl(completed.id(), 0), physician, "{\"decision\":\"ACCEPTED\"}");
        submit(reviewUrl(completed.id(), 1), physician, "{\"decision\":\"ACCEPTED\"}");

        mockMvc.perform(get("/physician/home").session(physician))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 / 2")));
    }

    @Test
    void validationRejectsMissingDecision() throws Exception {
        CompletedCase completed = saveCase(2, "a", "b");
        submitExpect(reviewUrl(completed.id(), 0), loginPhysician(),
                "{\"decision\":\"\"}", 400);
    }

    @Test
    void validationRejectsUnknownDecision() throws Exception {
        CompletedCase completed = saveCase(2, "a", "b");
        submitExpect(reviewUrl(completed.id(), 0), loginPhysician(),
                "{\"decision\":\"MAYBE\"}", 400);
    }

    @Test
    void validationRejectsAmendedWithoutText() throws Exception {
        CompletedCase completed = saveCase(2, "a", "b");
        submitExpect(reviewUrl(completed.id(), 0), loginPhysician(),
                "{\"decision\":\"AMENDED\"}", 400);
    }

    @Test
    void validationRejectsOutOfRangeAnswerOrder() throws Exception {
        CompletedCase completed = saveCase(2, "a", "b");
        submitExpect(reviewUrl(completed.id(), 99), loginPhysician(),
                "{\"decision\":\"ACCEPTED\"}", 400);
    }

    @Test
    void unknownCaseReturnsNotFound() throws Exception {
        submitExpect("/physician/cases/case-unknown/answers/0/review",
                loginPhysician(), "{\"decision\":\"ACCEPTED\"}", 404);
    }

    @Test
    void reviewPostWithoutCsrfIsRejected() throws Exception {
        CompletedCase completed = saveCase(2, "a", "b");
        mockMvc.perform(post(reviewUrl(completed.id(), 0))
                        .session(loginPhysician())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPTED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCannotSubmitReviewDecision() throws Exception {
        CompletedCase completed = saveCase(2, "a", "b");
        mockMvc.perform(post(reviewUrl(completed.id(), 0))
                        .session(loginPatient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPTED\"}")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewDecisionIsAudited() throws Exception {
        CompletedCase completed = saveCase(2, "a", "b");
        submit(reviewUrl(completed.id(), 1), loginPhysician(), "{\"decision\":\"REJECTED\"}");

        var events = auditEventRepository.findByCaseIdOrderByOccurredAtAsc(completed.id());
        assertThat(events).hasSize(1);
        AuditEvent event = events.getFirst();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.PHYSICIAN_REVIEW);
        assertThat(event.getOperation()).isEqualTo("REJECTED");
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getActorUsername()).isEqualTo("physician");
        assertThat(event.getActorRole()).isEqualTo("PHYSICIAN");
        assertThat(event.getResourceType()).isEqualTo("ReviewEntry");
        assertThat(event.getCaseId()).isEqualTo(completed.id());
    }

    @Test
    void workspaceShowsReviewControlsButNotForPatient() throws Exception {
        CompletedCase completed = saveCase(2, "a", "b");

        mockMvc.perform(get("/physician/cases/" + completed.id()).session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("review-accept-btn")))
                .andExpect(content().string(containsString("Not reviewed")))
                .andExpect(content().string(not(containsString("mark-reviewed-btn"))));
    }
}