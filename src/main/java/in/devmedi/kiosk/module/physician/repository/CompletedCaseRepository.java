package in.devmedi.kiosk.module.physician.repository;

import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompletedCaseRepository extends JpaRepository<CompletedCaseEntity, Long> {

    Optional<CompletedCaseEntity> findByCaseId(String caseId);

    /**
     * @return the most recently created completed case, if any
     */
    Optional<CompletedCaseEntity> findTopByOrderByCreatedAtDesc();

    /**
     * @return the most recently created completed case belonging to the given user, if any
     */
    Optional<CompletedCaseEntity> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}