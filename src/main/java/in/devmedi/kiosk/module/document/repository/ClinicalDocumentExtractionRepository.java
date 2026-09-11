package in.devmedi.kiosk.module.document.repository;

import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClinicalDocumentExtractionRepository extends JpaRepository<ClinicalDocumentExtraction, Long> {

    Optional<ClinicalDocumentExtraction> findByDocument(ClinicalDocument document);

    Optional<ClinicalDocumentExtraction> findByDocument_DocumentIdAndDocument_CompletedCase_CaseId(
            String documentId, String caseId);

    List<ClinicalDocumentExtraction> findByDocumentIn(List<ClinicalDocument> documents);

    @Query("select e from ClinicalDocumentExtraction e join fetch e.pages where e.document.documentId = :documentId")
    Optional<ClinicalDocumentExtraction> findByDocumentIdWithPages(@Param("documentId") String documentId);
}