package in.devmedi.kiosk.module.clinical.triage;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.redflag.RedFlag;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages red-flag triage assessments persisted against completed cases.
 *
 * <p>On case completion this service auto-creates rows for every detected red
 * flag (source {@link TriageSource#SYSTEM_DETECTED}) so physicians always see
 * the full triage picture. Physicians then take documented actions (SEEN /
 * ESCALATED / CLEARED / RESOLVED) which are captured with provenance and a
 * timestamp.</p>
 *
 * <p>The flag itself is always re-derived deterministically; this service
 * records only the human overlay.</p>
 */
@Service
public class RedFlagAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(RedFlagAssessmentService.class);

    private final RedFlagAssessmentRepository assessmentRepository;
    private final CompletedCaseRepository completedCaseRepository;
    private final UserRepository userRepository;

    public RedFlagAssessmentService(RedFlagAssessmentRepository assessmentRepository,
                                    CompletedCaseRepository completedCaseRepository,
                                    UserRepository userRepository) {
        this.assessmentRepository = assessmentRepository;
        this.completedCaseRepository = completedCaseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Auto-creates {@link TriageSource#SYSTEM_DETECTED} assessments for every
     * detected red flag. Idempotent: duplicate flags are skipped (unique
     * constraint on case_id+flag_id).
     */
    @Transactional
    public void recordSystemDetectedFlags(String caseId, List<RedFlag> flags) {
        if (flags == null || flags.isEmpty()) {
            return;
        }
        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown case: " + caseId));
        for (RedFlag flag : flags) {
            try {
                assessmentRepository.findByCompletedCase_CaseIdAndFlagId(caseId, flag.id())
                        .ifPresentOrElse(
                                existing -> log.debug("Red-flag assessment for '{}' on case '{}' already exists", flag.id(), caseId),
                                () -> assessmentRepository.save(new RedFlagAssessment(
                                        caseEntity,
                                        flag.id(),
                                        TriageSeverity.URGENT,
                                        TriageSource.SYSTEM_DETECTED,
                                        null,
                                        FlagAssessmentAction.SEEN,
                                        null)));
            } catch (RuntimeException ex) {
                log.warn("Failed to persist red-flag assessment for '{}' on case '{}': {}", flag.id(), caseId, ex.getMessage());
            }
        }
    }

    /**
     * Returns all assessments for a case, ordered by assessed time ascending.
     */
    @Transactional(readOnly = true)
    public List<RedFlagAssessment> assessmentsForCase(String caseId) {
        return assessmentRepository.findByCompletedCase_CaseIdOrderByAssessedAtAsc(caseId);
    }

    /**
     * Physician takes a documented action on a triage flag.
     */
    @Transactional
    public void physicianAction(String caseId,
                                String flagId,
                                String action,
                                String note,
                                Long physicianUserId) {
        RedFlagAssessment existing = assessmentRepository
                .findByCompletedCase_CaseIdAndFlagId(caseId, flagId)
                .orElse(null);
        if (existing == null) {
            throw new IllegalArgumentException("No triage assessment found for flag '" + flagId + "' on case '" + caseId + "'");
        }
        FlagAssessmentAction resolvedAction = FlagAssessmentAction.parseStrict(action);
        if (resolvedAction == null) {
            throw new IllegalArgumentException("Invalid action: " + action);
        }
        User physician = physicianUserId != null ? userRepository.getReferenceById(physicianUserId) : null;
        log.info("Physician action on case='{}' flag='{}' action='{}' by userId={}", caseId, flagId, resolvedAction, physicianUserId);
        existing.assess(physician, resolvedAction, note);
        assessmentRepository.save(existing);
    }

    /** Count of flagged assessments for a case. */
    @Transactional(readOnly = true)
    public long flaggedCount(String caseId) {
        return assessmentsForCase(caseId).stream()
                .filter(a -> a.getSeverity() != TriageSeverity.NONE)
                .count();
    }

    /** Highest severity across all assessments for a case. */
    @Transactional(readOnly = true)
    public TriageSeverity overallSeverity(String caseId) {
        return assessmentsForCase(caseId).stream()
                .map(RedFlagAssessment::getSeverity)
                .reduce(TriageSeverity.NONE, TriageSeverity::highest);
    }
}