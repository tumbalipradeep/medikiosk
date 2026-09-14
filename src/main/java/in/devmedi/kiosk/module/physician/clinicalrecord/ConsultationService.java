package in.devmedi.kiosk.module.physician.clinicalrecord;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Manages the physician's final consultation record for a case.
 *
 * <p>The consultation is physician-authored (assessment, plan, advice,
 * follow-up) and is the FINAL CLINICAL RECORD after the review loop.
 * AI summaries live separately and are never copied into this record without
 * an explicit physician action.</p>
 */
@Service
public class ConsultationService {

    private static final Logger log = LoggerFactory.getLogger(ConsultationService.class);

    private final ConsultationRepository repository;
    private final CompletedCaseRepository completedCaseRepository;
    private final UserRepository userRepository;

    public ConsultationService(ConsultationRepository repository,
                               CompletedCaseRepository completedCaseRepository,
                               UserRepository userRepository) {
        this.repository = repository;
        this.completedCaseRepository = completedCaseRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Consultation> forCase(String caseId) {
        return repository.findByCompletedCase_CaseId(caseId);
    }

    @Transactional
    public Consultation createOrUpdate(String caseId,
                                       Long physicianUserId,
                                       String assessment,
                                       String plan,
                                       String advice,
                                       String followUp) {
        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown case: " + caseId));
        User physician = userRepository.getReferenceById(physicianUserId);
        Consultation consultation = repository.findByCompletedCase_CaseId(caseId)
                .orElseGet(() -> {
                    log.info("Creating new consultation for case='{}' by userId={}", caseId, physicianUserId);
                    return new Consultation(caseEntity, physician);
                });
        consultation.update(assessment, plan, advice, followUp);
        return repository.save(consultation);
    }

    @Transactional
    public void finalizeRecord(String caseId, Long physicianUserId) {
        Consultation consultation = repository.findByCompletedCase_CaseId(caseId)
                .orElseThrow(() -> new IllegalArgumentException("No consultation found for case '" + caseId + "'"));
        consultation.finalizeRecord();
        repository.save(consultation);
        log.info("Consultation finalized for case='{}' by userId={}", caseId, physicianUserId);
    }
}