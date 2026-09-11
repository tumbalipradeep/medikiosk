package in.devmedi.kiosk.module.document.findings.repository;

import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClinicalDocumentFindingsRepository extends JpaRepository<ClinicalDocumentFindings, Long> {

    Optional<ClinicalDocumentFindings> findByExtraction(ClinicalDocumentExtraction extraction);
}