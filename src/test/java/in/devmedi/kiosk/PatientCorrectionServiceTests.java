package in.devmedi.kiosk.module.patient.correction;

import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import in.devmedi.kiosk.module.audit.service.AuditEventCommand;
import in.devmedi.kiosk.module.audit.service.AuditService;
import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseAnswerEntity;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseAnswerRepository;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests for the RD2 checkpoint fixes on the correction service:
 * the unique-constraint race must surface as a controlled 409 (never a raw
 * 500 or a database detail), and the correction lifecycle exposes exactly one
 * application-written state (SUBMITTED) — the physician decision lives in
 * physician_review_entries / PHYSICIAN_REVIEW audit, not here.
 */
class PatientCorrectionServiceTests {

    private static final String CASE_ID = "case-race-test";

    private CompletedCaseRepository caseRepository;
    private CompletedCaseAnswerRepository answerRepository;
    private PatientAnswerCorrectionRepository correctionRepository;
    private AuditService auditService;
    private PatientCorrectionService service;

    private User owner;
    private CompletedCaseEntity ownedCase;

    @BeforeEach
    void setUp() {
        caseRepository = mock(CompletedCaseRepository.class);
        answerRepository = mock(CompletedCaseAnswerRepository.class);
        correctionRepository = mock(PatientAnswerCorrectionRepository.class);
        auditService = mock(AuditService.class);
        service = new PatientCorrectionService(caseRepository, answerRepository,
                correctionRepository, auditService);

        owner = new User("patient", "hashed", "Patient", Role.PATIENT);
        // The User id is JPA-assigned; assign it reflectively for this unit test.
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(owner, 42L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        ownedCase = new CompletedCaseEntity(CASE_ID);
        ownedCase.setUser(owner);
        CompletedCaseAnswerEntity answer = CompletedCaseAnswerEntity.from(ownedCase, 0,
                new ClinicalAnswer("chief_complaint_symptom", "CHIEF_COMPLAINT",
                        "SYMPTOM_PROBLEM", "Describe your problem", "Original recorded answer"));

        when(caseRepository.findByCaseId(CASE_ID)).thenReturn(Optional.of(ownedCase));
        when(answerRepository.findByCompletedCase_CaseIdAndAnswerOrder(CASE_ID, 0))
                .thenReturn(Optional.of(answer));
        when(correctionRepository.findByCompletedCase_CaseIdOrderByAnswerOrder(CASE_ID))
                .thenReturn(List.of());
    }

    @Test
    void uniqueConstraintRaceSurfacesAsControlled409WithoutDatabaseDetails() {
        when(correctionRepository.save(any(PatientAnswerCorrection.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement [Unique index violation: "
                                + "PUBLIC.UQ_PATIENT_ANSWER_CORRECTIONS_ORDER VALUES ('case-x', 0)]"));

        assertThatThrownBy(() -> service.correct(CASE_ID, owner.getId(), owner.getUsername(),
                0, "Corrected answer", null, "/test"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageNotContaining("UQ_PATIENT_ANSWER_CORRECTIONS")
                .hasMessageNotContaining("could not execute statement");
    }

    @Test
    void successfulCorrectionIsAuditedAsPatientCorrection() {
        when(correctionRepository.save(any(PatientAnswerCorrection.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PatientAnswerCorrection saved = service.correct(CASE_ID, owner.getId(),
                owner.getUsername(), 0, "Corrected answer", "reason", "/test");

        assertThat(saved.getStatus()).isEqualTo(PatientAnswerCorrection.CorrectionStatus.SUBMITTED);
        assertThat(saved.getOriginalAnswer()).isEqualTo("Original recorded answer");
        verify(auditService).record(ArgumentMatchers.argThat((AuditEventCommand cmd) ->
                cmd.eventType() == AuditEventType.PATIENT_CORRECTION
                        && cmd.actorUsername().equals("patient")
                        && CASE_ID.equals(cmd.caseId())));
    }

    @Test
    void correctionStatusHasExactlyOneApplicationWrittenValue() {
        // The physician decision is a separate artifact (physician_review_entries
        // + PHYSICIAN_REVIEW audit); the correction itself only ever sits in the
        // submitted state awaiting that decision.
        assertThat(PatientAnswerCorrection.CorrectionStatus.values())
                .containsExactly(PatientAnswerCorrection.CorrectionStatus.SUBMITTED);
    }
}
