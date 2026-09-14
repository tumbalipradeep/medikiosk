package in.devmedi.kiosk.module.profile.entity;

import in.devmedi.kiosk.module.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PhysicianProfileRepository extends JpaRepository<PhysicianProfile, Long> {
    Optional<PhysicianProfile> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    List<PhysicianProfile> findByDepartmentIgnoreCase(String department);
    List<PhysicianProfile> findByOrganizationIgnoreCase(String organization);
}