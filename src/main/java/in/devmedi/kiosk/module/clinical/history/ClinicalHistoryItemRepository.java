package in.devmedi.kiosk.module.clinical.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClinicalHistoryItemRepository extends JpaRepository<ClinicalHistoryItem, Long> {

    List<ClinicalHistoryItem> findByPatientIdOrderByCategoryAscConceptKeyAsc(Long patientUserId);

    List<ClinicalHistoryItem> findByPatientIdAndCategoryOrderByConceptKeyAsc(
            Long patientUserId, ClinicalHistoryCategory category);

    Optional<ClinicalHistoryItem> findByPatientIdAndCategoryAndConceptKey(
            Long patientUserId, ClinicalHistoryCategory category, String conceptKey);

    boolean existsByPatientIdAndCategoryAndConceptKey(
            Long patientUserId, ClinicalHistoryCategory category, String conceptKey);

    long countByPatientId(Long patientUserId);
}