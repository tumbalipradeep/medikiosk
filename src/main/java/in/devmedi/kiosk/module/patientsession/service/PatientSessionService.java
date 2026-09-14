package in.devmedi.kiosk.module.patientsession.service;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import in.devmedi.kiosk.module.consent.service.ConsentService;
import in.devmedi.kiosk.module.encounter.EncounterService;
import in.devmedi.kiosk.module.patientsession.entity.PatientSession;
import in.devmedi.kiosk.module.patientsession.entity.PatientSessionStatus;
import in.devmedi.kiosk.module.patientsession.repository.PatientSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientSessionService {

    private final PatientSessionRepository patientSessionRepository;
    private final UserRepository userRepository;
    private final ConsentService consentService;
    private final EncounterService encounterService;

    public PatientSessionService(PatientSessionRepository patientSessionRepository,
                                 UserRepository userRepository,
                                 ConsentService consentService,
                                 EncounterService encounterService) {
        this.patientSessionRepository = patientSessionRepository;
        this.userRepository = userRepository;
        this.consentService = consentService;
        this.encounterService = encounterService;
    }

    /**
     * Starts an active patient session. Requires granted CLINICAL_CASE_TAKING consent
     * and no pre-existing active session. Throws {@link IllegalStateException} on either condition.
     *
     * <p>The single-active check and the insert are serialized per user so two
     * concurrent start requests can never create more than one active session.</p>
     *
     * <p>An in-progress clinical encounter is created alongside the session, so
     * the whole visit (intake + review lifecycle) is tracked from the start.</p>
     *
     * @return the newly created active session
     */
    @Transactional
    public PatientSession start(Long userId) {
        if (!consentService.hasGranted(userId, ConsentType.CLINICAL_CASE_TAKING)) {
            throw new IllegalStateException("Clinical case-taking consent has not been granted.");
        }
        synchronized (("patient-session-start:" + userId).intern()) {
            if (hasActiveSession(userId)) {
                throw new IllegalStateException("An active patient session already exists.");
            }
            User user = userRepository.getReferenceById(userId);
            PatientSession session = new PatientSession(user);
            patientSessionRepository.save(session);
            encounterService.startOrGetCurrent(userId);
            return session;
        }
    }

    @Transactional(readOnly = true)
    public boolean hasActiveSession(Long userId) {
        return patientSessionRepository.existsByUserIdAndStatus(userId, PatientSessionStatus.ACTIVE);
    }

    /**
     * Marks the patient's active session as completed. Called once the intake
     * case has been persisted so a genuinely finished patient can start a new
     * session. A session is completed only from the ACTIVE state; anything else
     * is a no-op and never throws.
     */
    @Transactional
    public void complete(Long userId) {
        patientSessionRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(userId, PatientSessionStatus.ACTIVE)
                .ifPresent(session -> {
                    session.markCompleted();
                    patientSessionRepository.save(session);
                });
    }
}
