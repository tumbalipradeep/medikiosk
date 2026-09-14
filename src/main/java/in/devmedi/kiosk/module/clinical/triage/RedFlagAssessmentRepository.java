package in.devmedi.kiosk.module.clinical.triage;

import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RedFlagAssessmentRepository extends JpaRepository<RedFlagAssessment, Long> {

    List<RedFlagAssessment> findByCompletedCase_CaseIdOrderByAssessedAtAsc(String caseId);

    Optional<RedFlagAssessment> findByCompletedCase_CaseIdAndFlagId(String caseId, String flagId);
}