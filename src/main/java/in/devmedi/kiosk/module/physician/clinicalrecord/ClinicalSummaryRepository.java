package in.devmedi.kiosk.module.physician.clinicalrecord;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.clinical.provenance.ClinicalProvenance;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClinicalSummaryRepository extends JpaRepository<ClinicalSummary, Long> {

    Optional<ClinicalSummary> findByCompletedCase_CaseIdAndSection(String caseId, String section);

    List<ClinicalSummary> findByCompletedCase_CaseIdOrderBySectionAsc(String caseId);

    boolean existsByCompletedCase_CaseIdAndSection(String caseId, String section);
}