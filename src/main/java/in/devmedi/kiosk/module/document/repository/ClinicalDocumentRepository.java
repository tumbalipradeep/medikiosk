package in.devmedi.kiosk.module.document.repository;

import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClinicalDocumentRepository extends JpaRepository<ClinicalDocument, Long> {

    Optional<ClinicalDocument> findByDocumentId(String documentId);

    List<ClinicalDocument> findByCompletedCase_CaseIdOrderByUploadedAtAsc(String caseId);

    List<ClinicalDocument> findByCompletedCase_CaseIdAndUser_IdOrderByUploadedAtAsc(String caseId, Long userId);

    long countByCompletedCase_CaseId(String caseId);

    void deleteByCompletedCase_CaseId(String caseId);
}
