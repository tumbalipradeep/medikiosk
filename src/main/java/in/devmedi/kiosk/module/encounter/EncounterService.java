package in.devmedi.kiosk.module.encounter;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Encounter lifecycle: start → submit (with the persisted clinical case id) →
 * under review → completed, plus cancellation of abandoned in-progress
 * encounters.
 *
 * <p>The encounter is the single workflow container for a visit. The patient
 * session covers the intake kiosk flow; the encounter additionally tracks what
 * physicians did with the submitted clinical record.</p>
 */
@Service
public class EncounterService {

    private final EncounterRepository encounterRepository;
    private final UserRepository userRepository;

    public EncounterService(EncounterRepository encounterRepository, UserRepository userRepository) {
        this.encounterRepository = encounterRepository;
        this.userRepository = userRepository;
    }

    /**
     * Starts a new in-progress encounter for the patient, or returns the
     * existing in-progress one. Creation is serialized per user so concurrent
     * start requests can never double-create.
     */
    @Transactional
    public Encounter startOrGetCurrent(Long userId) {
        synchronized (("encounter-start:" + userId).intern()) {
            return encounterRepository
                    .findFirstByPatientIdAndStatusOrderByStartedAtDesc(userId, EncounterStatus.IN_PROGRESS)
                    .orElseGet(() -> {
                        User user = userRepository.getReferenceById(userId);
                        return encounterRepository.save(new Encounter(user));
                    });
        }
    }

    @Transactional(readOnly = true)
    public Optional<Encounter> currentInProgress(Long userId) {
        return encounterRepository
                .findFirstByPatientIdAndStatusOrderByStartedAtDesc(userId, EncounterStatus.IN_PROGRESS);
    }

    @Transactional(readOnly = true)
    public Optional<Encounter> latestSubmitted(Long userId) {
        return encounterRepository.findFirstByPatientIdAndStatusInOrderByStartedAtDesc(userId,
                List.of(EncounterStatus.SUBMITTED, EncounterStatus.UNDER_REVIEW));
    }

    @Transactional(readOnly = true)
    public Optional<Encounter> findByCaseId(String caseId) {
        return encounterRepository.findByCaseId(caseId);
    }

    @Transactional(readOnly = true)
    public Optional<Encounter> findById(Long id) {
        return encounterRepository.findById(id);
    }

    /**
     * Submits the current in-progress encounter with the persisted case id.
     * Idempotent: if the encounter is already submitted (double-submit guard),
     * the existing state is returned without error.
     */
    @Transactional
    public void submit(Long userId, String caseId) {
        encounterRepository
                .findFirstByPatientIdAndStatusOrderByStartedAtDesc(userId, EncounterStatus.IN_PROGRESS)
                .ifPresent(encounter -> {
                    encounter.submit(caseId);
                    encounterRepository.save(encounter);
                });
    }

    @Transactional
    public void markUnderReview(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(encounter -> {
            encounter.markUnderReview();
            encounterRepository.save(encounter);
        });
    }

    @Transactional
    public void markUnderReviewByCaseId(String caseId) {
        encounterRepository.findByCaseId(caseId).ifPresent(encounter -> {
            encounter.markUnderReview();
            encounterRepository.save(encounter);
        });
    }

    @Transactional
    public void complete(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(encounter -> {
            encounter.complete();
            encounterRepository.save(encounter);
        });
    }

    @Transactional
    public void completeByCaseId(String caseId) {
        encounterRepository.findByCaseId(caseId).ifPresent(encounter -> {
            encounter.complete();
            encounterRepository.save(encounter);
        });
    }

    /**
     * Cancels in-progress encounters that have been idle beyond the given
     * threshold. Returning patients who abandoned a session get a clean slate
     * instead of a resurrected intake.
     */
    @Transactional
    public int cancelStaleInProgress(long maxIdleMinutes) {
        Instant threshold = Instant.now().minus(maxIdleMinutes, ChronoUnit.MINUTES);
        List<Encounter> stale = encounterRepository
                .findByStatusAndUpdatedAtBefore(EncounterStatus.IN_PROGRESS, threshold);
        stale.forEach(encounter -> {
            encounter.cancel();
            encounterRepository.save(encounter);
        });
        return stale.size();
    }

    @Transactional(readOnly = true)
    public long countByStatus(EncounterStatus status) {
        return encounterRepository.countByStatus(status);
    }
}