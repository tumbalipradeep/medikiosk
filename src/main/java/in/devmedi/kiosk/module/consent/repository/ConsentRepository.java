package in.devmedi.kiosk.module.consent.repository;

import in.devmedi.kiosk.module.consent.entity.Consent;
import in.devmedi.kiosk.module.consent.entity.ConsentState;
import in.devmedi.kiosk.module.consent.entity.ConsentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    Optional<Consent> findByUserIdAndConsentType(Long userId, ConsentType consentType);

    boolean existsByUserIdAndConsentTypeAndState(Long userId, ConsentType consentType, ConsentState state);

    List<Consent> findByUserIdOrderByConsentType(Long userId);

    List<Consent> findByUserId(Long userId);
}
