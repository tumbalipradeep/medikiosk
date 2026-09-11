package in.devmedi.kiosk.module.document.repository;

import in.devmedi.kiosk.module.document.entity.ClinicalDocumentPageText;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalDocumentPageTextRepository extends JpaRepository<ClinicalDocumentPageText, Long> {
}