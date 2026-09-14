package in.devmedi.kiosk.module.physician.clinicalrecord;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {

    Optional<Consultation> findByCompletedCase_CaseId(String caseId);

    boolean existsByCompletedCase_CaseId(String caseId);
}