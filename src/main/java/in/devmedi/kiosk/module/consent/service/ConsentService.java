package in.devmedi.kiosk.module.consent.service;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.consent.entity.Consent;
import in.devmedi.kiosk.module.consent.entity.ConsentState;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import in.devmedi.kiosk.module.consent.repository.ConsentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

@Service
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final UserRepository userRepository;

    public ConsentService(ConsentRepository consentRepository, UserRepository userRepository) {
        this.consentRepository = consentRepository;
        this.userRepository = userRepository;
    }

    public static String purpose(ConsentType type) {
        return switch (type) {
            case CLINICAL_CASE_TAKING ->
                    "To record a structured clinical history for your medical case during this session.";
            case AUDIO_CAPTURE ->
                    "To capture and transcribe your spoken answers for the health history, if audio input is used.";
            case DOCUMENT_PROCESSING ->
                    "To read and understand documents (such as prescriptions or reports) you choose to share.";
            case DATA_SHARING ->
                    "To share relevant case information with your physician for continuity of care.";
        };
    }

    @Transactional(readOnly = true)
    public Map<ConsentType, ConsentState> currentStates(Long userId) {
        Map<ConsentType, ConsentState> states = new EnumMap<>(ConsentType.class);
        for (ConsentType type : ConsentType.values()) {
            consentRepository.findByUserIdAndConsentType(userId, type)
                    .ifPresent(c -> states.put(type, c.getState()));
        }
        return states;
    }

    @Transactional
    public void grant(Long userId, ConsentType type) {
        Consent consent = findByUserAndType(userId, type).orElseGet(() -> {
            User user = userRepository.getReferenceById(userId);
            return new Consent(user, type, purpose(type));
        });
        consent.grant();
        consentRepository.save(consent);
    }

    @Transactional
    public void revoke(Long userId, ConsentType type) {
        Consent consent = findByUserAndType(userId, type).orElseGet(() -> {
            User user = userRepository.getReferenceById(userId);
            return new Consent(user, type, purpose(type));
        });
        consent.revoke();
        consentRepository.save(consent);
    }

    @Transactional(readOnly = true)
    public boolean hasGranted(Long userId, ConsentType type) {
        return consentRepository.existsByUserIdAndConsentTypeAndState(userId, type, ConsentState.GRANTED);
    }

    private java.util.Optional<Consent> findByUserAndType(Long userId, ConsentType type) {
        return consentRepository.findByUserIdAndConsentType(userId, type);
    }
}
