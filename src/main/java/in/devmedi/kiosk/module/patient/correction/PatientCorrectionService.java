package in.devmedi.kiosk.module.patient.correction;

import in.devmedi.kiosk.module.audit.entity.AuditEventType;
import in.devmedi.kiosk.module.audit.entity.AuditOutcome;
import in.devmedi.kiosk.module.audit.service.AuditEventCommand;
import in.devmedi.kiosk.module.audit.service.AuditService;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseAnswerEntity;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseAnswerRepository;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Patient correction loop for captured intake answers.
 *
 * <p>A patient may correct ONLY an answer of their OWN case. Corrections are
 * additive: the persisted original answer is never modified — each correction
 * stores an immutable server-side snapshot of the original answer at
 * submission time, and the submission is rejected when the corrected value
 * equals the snapshot (so a correction can never silently apply to different
 * clinical evidence than the patient saw).</p>
 *
 * <p>Every accepted correction writes a {@code PATIENT_CORRECTION} audit event.
 * A physician's clinical decision about which value to use remains the
 * distinct {@code PHYSICIAN_REVIEW} flow; corrections are presented to
 * physicians as provenance beside the original, never as a silent rewrite.</p>
 */
@Service
public class PatientCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(PatientCorrectionService.class);

    private final CompletedCaseRepository completedCaseRepository;
    private final CompletedCaseAnswerRepository answerRepository;
    private final PatientAnswerCorrectionRepository correctionRepository;
    private final AuditService auditService;

    public PatientCorrectionService(CompletedCaseRepository completedCaseRepository,
                                    CompletedCaseAnswerRepository answerRepository,
                                    PatientAnswerCorrectionRepository correctionRepository,
                                    AuditService auditService) {
        this.completedCaseRepository = completedCaseRepository;
        this.answerRepository = answerRepository;
        this.correctionRepository = correctionRepository;
        this.auditService = auditService;
    }

    // ─── Patient side ─────────────────────────────────────────────────

    /**
     * Lists corrections for a case owned by the given patient, keyed by answer
     * order for easy merging into the review view.
     */
    @Transactional(readOnly = true)
    public Map<Integer, PatientAnswerCorrection> correctionsByOrder(String caseId, Long patientUserId) {
        requireOwnedCase(caseId, patientUserId);
        Map<Integer, PatientAnswerCorrection> byOrder = new HashMap<>();
        for (PatientAnswerCorrection c : correctionRepository.findByCompletedCase_CaseIdOrderByAnswerOrder(caseId)) {
            byOrder.put(c.getAnswerOrder(), c);
        }
        return byOrder;
    }

    /**
     * Submits (or replaces) the patient's correction for one answer of their
     * own case. The original-answer snapshot is taken from the persisted row,
     * never from the request.
     */
    @Transactional
    public PatientAnswerCorrection correct(String caseId,
                                           Long patientUserId,
                                           String patientUsername,
                                           int answerOrder,
                                           String correctedAnswer,
                                           String reason,
                                           String requestPath) {
        if (correctedAnswer == null || correctedAnswer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corrected answer must not be empty.");
        }
        if (correctedAnswer.length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corrected answer is too long.");
        }
        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmedReason != null && trimmedReason.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correction reason is too long.");
        }

        CompletedCaseEntity owned = requireOwnedCase(caseId, patientUserId);
        CompletedCaseAnswerEntity answer = answerRepository
                .findByCompletedCase_CaseIdAndAnswerOrder(caseId, answerOrder)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found for this case."));

        String originalSnapshot = answer.getAnswer();
        if (correctedAnswer.trim().equals(originalSnapshot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The corrected answer is the same as the original answer.");
        }

        try {
            // Replace any pending correction atomically; the snapshot is re-taken
            // from the persisted row on every submission so a correction can never
            // attach to evidence other than what the patient actually saw.
            correctionRepository.findByCompletedCase_CaseIdOrderByAnswerOrder(caseId).stream()
                    .filter(c -> c.getAnswerOrder() == answerOrder)
                    .findFirst()
                    .ifPresent(existing -> {
                        correctionRepository.delete(existing);
                        // Flush immediately so the DELETE reaches the database before
                        // the replacement INSERT (unique constraint on case+order).
                        correctionRepository.flush();
                    });

            PatientAnswerCorrection correction = PatientAnswerCorrection.submit(
                    owned, answerOrder, originalSnapshot, correctedAnswer.trim(), trimmedReason,
                    patientUsername, Instant.now());
            PatientAnswerCorrection saved = correctionRepository.save(correction);
            log.info("patient.correction caseId={} answerOrder={} actor={}", caseId, answerOrder, patientUsername);
            auditService.record(new AuditEventCommand(
                    AuditEventType.PATIENT_CORRECTION, Instant.now(), patientUsername, "PATIENT",
                    caseId, "CORRECT", "AnswerCorrection", AuditOutcome.SUCCESS, null, requestPath));
            return saved;
        } catch (DataIntegrityViolationException raced) {
            // Two concurrent submissions for the same answer raced the
            // (case_id, answer_order) unique constraint. The database rejected
            // the loser; the winner's correction stands and this transaction
            // rolls back untouched. The client gets a recoverable 409 — never
            // a raw 500 or any database detail.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This answer was corrected from another submission. Please reload your review to see the current state.");
        }
    }

    // ─── Physician side (read-only provenance) ────────────────────────

    /** All corrections for a case, for physician-side provenance display. */
    @Transactional(readOnly = true)
    public List<PatientAnswerCorrection> correctionsForCase(String caseId) {
        return correctionRepository.findByCompletedCase_CaseIdOrderByAnswerOrder(caseId);
    }

    /** One correction by case + order, if present. */
    @Transactional(readOnly = true)
    public Optional<PatientAnswerCorrection> correctionForAnswer(String caseId, int answerOrder) {
        return correctionRepository.findByCompletedCase_CaseIdOrderByAnswerOrder(caseId).stream()
                .filter(c -> c.getAnswerOrder() == answerOrder)
                .findFirst();
    }

    // ─── Ownership boundary ───────────────────────────────────────────

    /**
     * Loads the case only when it belongs to the given patient; any other
     * caller gets a 404 that does not reveal the existence of foreign cases.
     */
    private CompletedCaseEntity requireOwnedCase(String caseId, Long patientUserId) {
        if (patientUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return completedCaseRepository.findByCaseId(caseId)
                .filter(c -> c.getUser() != null && patientUserId.equals(c.getUser().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Case not found for this patient"));
    }

    /** Correction payload for the patient review UI. */
    public record CorrectionView(int answerOrder,
                                 String originalAnswer,
                                 String correctedAnswer,
                                 String reason,
                                 String correctedBy,
                                 Instant correctedAt,
                                 String status) {

        public static CorrectionView from(PatientAnswerCorrection c) {
            return new CorrectionView(c.getAnswerOrder(), c.getOriginalAnswer(), c.getCorrectedAnswer(),
                    c.getReason(), c.getCorrectedBy(), c.getCorrectedAt(), c.getStatus().name());
        }
    }
}
