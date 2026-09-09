package in.devmedi.kiosk.module.patientsession.service;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import in.devmedi.kiosk.module.consent.service.ConsentService;
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

    public PatientSessionService(PatientSessionRepository patientSessionRepository,
                                 UserRepository userRepository,
                                 ConsentService consentService) {
        this.patientSessionRepository = patientSessionRepository;
        this.userRepository = userRepository;
        this.consentService = consentService;
    }

    /**
     * Starts an active patient session. Requires granted CLINICAL_CASE_TAKING consent
     * and no pre-existing active session. Throws {@link IllegalStateException} on either condition.
     *
     * @return the newly created active session
     */
    @Transactional
    public PatientSession start(Long userId) {
        if (!consentService.hasGranted(userId, ConsentType.CLINICAL_CASE_TAKING)) {
            throw new IllegalStateException("Clinical case-taking consent has not been granted.");
        }
        if (hasActiveSession(userId)) {
            throw new IllegalStateException("An active patient session already exists.");
        }
        User user = userRepository.getReferenceById(userId);
        PatientSession session = new PatientSession(user);
        return patientSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveSession(Long userId) {
        return patientSessionRepository.existsByUserIdAndStatus(userId, PatientSessionStatus.ACTIVE);
    }
}
