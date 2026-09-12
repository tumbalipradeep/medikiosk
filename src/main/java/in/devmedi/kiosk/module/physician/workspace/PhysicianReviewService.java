package in.devmedi.kiosk.module.physician.workspace;

import in.devmedi.kiosk.module.audit.entity.AuditOutcome;
import in.devmedi.kiosk.module.audit.service.AuditEventCommand;
import in.devmedi.kiosk.module.audit.service.AuditService;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseAnswerEntity;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseAnswerRepository;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import in.devmedi.kiosk.module.physician.review.PhysicianReviewEntry;
import in.devmedi.kiosk.module.physician.review.PhysicianReviewEntryRepository;
import in.devmedi.kiosk.module.physician.review.ReviewDecision;
import in.devmedi.kiosk.module.physician.review.ReviewRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Accepts, amends, or rejects physician review decisions against one
 * persisted intake answer.
 *
 * <p>The original patient evidence stored in {@code completed_case_answers}
 * is never overwritten by a review decision. {@code AMENDED} stores a
 * corrected physician text alongside the original; {@code REJECTED} records
 * that the physician excluded the answer from clinical use. Each decision is
 * keyed uniquely on the stable pair {@code (case_id, answer_order)}.</p>
 *
 * <p>Every decision is audited immediately via {@link AuditService} with its
 * own {@code REQUIRES_NEW} transaction, so an audit write failure cannot
 * corrupt or roll back the review decision.</p>
 */
@Service
public class PhysicianReviewService {

    private final CompletedCaseRepository completedCaseRepository;
    private final CompletedCaseAnswerRepository answerRepository;
    private final PhysicianReviewEntryRepository reviewRepository;
    private final AuditService auditService;

    public PhysicianReviewService(CompletedCaseRepository completedCaseRepository,
                                  CompletedCaseAnswerRepository answerRepository,
                                  PhysicianReviewEntryRepository reviewRepository,
                                  AuditService auditService) {
        this.completedCaseRepository = completedCaseRepository;
        this.answerRepository = answerRepository;
        this.reviewRepository = reviewRepository;
        this.auditService = auditService;
    }

    /**
     * Saves (or replaces) a physician review decision for one answer.
     *
     * <p>The read-modify-write cycle is serialized per {@code (caseId,
     * answerOrder)} so a rapid repeated or concurrent submission upserts a
     * single row instead of racing into a duplicate-key violation. The unique
     * {@code (case_id, answer_order)} key remains the final guard.</p>
     *
     * @return the persisted entry
     * @throws CaseNotFoundException       if the case does not exist
     * @throws ReviewValidationException   if the request is invalid
     */
    @Transactional
    public PhysicianReviewEntry saveReview(String caseId,
                                           int answerOrder,
                                           ReviewRequest request,
                                           String reviewerUsername) {
        synchronized (("physician-review:" + caseId + ":" + answerOrder).intern()) {
            CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(caseId)
                    .orElseThrow(() -> new CaseNotFoundException(caseId));

            List<CompletedCaseAnswerEntity> answers =
                    answerRepository.findByCaseIdOrderByAnswerOrder(caseId);
            if (answerOrder < 0 || answerOrder >= answers.size()) {
                throw new ReviewValidationException("Answer order " + answerOrder
                        + " is outside the valid range 0.." + (answers.size() - 1));
            }

            ReviewDecision decision = parseDecision(request.decision());
            String amendedText = normalizeAmendedText(decision, request.amendedText());
            String rationale = normalizeRationale(request.rationale());

            Optional<PhysicianReviewEntry> existing = reviewRepository
                    .findByCompletedCase_CaseIdAndAnswerOrder(caseId, answerOrder);
            PhysicianReviewEntry entry = existing.orElseGet(() ->
                    PhysicianReviewEntry.of(caseEntity, answerOrder, decision,
                            amendedText, rationale, reviewerUsername));
            if (existing.isPresent()) {
                entry.apply(decision, amendedText, rationale, reviewerUsername);
            }
            reviewRepository.save(entry);

            auditService.record(AuditEventCommand.physicianReview(
                    reviewerUsername, "PHYSICIAN", caseId,
                    decision.name(), null, AuditOutcome.SUCCESS, null));

            return entry;
        }
    }

    /**
     * Returns all review decisions for a case, ordered by answer order.
     */
    @Transactional(readOnly = true)
    public List<PhysicianReviewEntry> reviews(String caseId) {
        if (completedCaseRepository.findByCaseId(caseId).isEmpty()) {
            throw new CaseNotFoundException(caseId);
        }
        return reviewRepository.findByCompletedCase_CaseIdOrderByAnswerOrder(caseId);
    }

    private ReviewDecision parseDecision(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ReviewValidationException("decision is required");
        }
        try {
            return ReviewDecision.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ReviewValidationException(
                    "decision must be ACCEPTED, AMENDED, or REJECTED");
        }
    }

    private String normalizeAmendedText(ReviewDecision decision, String text) {
        if (decision != ReviewDecision.AMENDED) {
            return null;
        }
        if (text == null || text.isBlank()) {
            throw new ReviewValidationException(
                    "amendedText is required when decision is AMENDED");
        }
        if (text.length() > 4000) {
            throw new ReviewValidationException("amendedText must not exceed 4000 characters");
        }
        return text.trim();
    }

    private String normalizeRationale(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.length() > 1000) {
            throw new ReviewValidationException("rationale must not exceed 1000 characters");
        }
        return text.trim();
    }
}