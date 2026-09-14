package in.devmedi.kiosk.module.encounter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    Optional<Encounter> findFirstByPatientIdAndStatusOrderByStartedAtDesc(
            Long patientUserId, EncounterStatus status);

    Optional<Encounter> findFirstByPatientIdAndStatusInOrderByStartedAtDesc(
            Long patientUserId, Collection<EncounterStatus> statuses);

    Optional<Encounter> findByCaseId(String caseId);

    boolean existsByPatientIdAndStatus(Long patientUserId, EncounterStatus status);

    List<Encounter> findByPatientIdOrderByStartedAtDesc(Long patientUserId);

    List<Encounter> findByStatusOrderByStartedAtDesc(EncounterStatus status);

    List<Encounter> findByStatusAndUpdatedAtBefore(EncounterStatus status, Instant updatedBefore);

    long countByStatus(EncounterStatus status);
}