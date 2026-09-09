package in.devmedi.kiosk.module.patientsession.repository;

import in.devmedi.kiosk.module.patientsession.entity.PatientSession;
import in.devmedi.kiosk.module.patientsession.entity.PatientSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientSessionRepository extends JpaRepository<PatientSession, Long> {

    Optional<PatientSession> findFirstByUserIdAndStatusOrderByStartedAtDesc(Long userId, PatientSessionStatus status);

    boolean existsByUserIdAndStatus(Long userId, PatientSessionStatus status);

    long countByUserIdAndStatus(Long userId, PatientSessionStatus status);

    List<PatientSession> findByUserId(Long userId);
}
