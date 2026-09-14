package in.devmedi.kiosk.module.profile.entity;

import in.devmedi.kiosk.module.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {
    Optional<PatientProfile> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}