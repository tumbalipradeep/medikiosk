package in.devmedi.kiosk.module.medication.repository;

import in.devmedi.kiosk.module.medication.entity.MedicationProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationProfileRepository extends JpaRepository<MedicationProfile, Long> {
}