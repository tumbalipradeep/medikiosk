package in.devmedi.kiosk.module.auth.repository;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    List<User> findByOrderByIdDesc();

    List<User> findByRoleOrderByIdDesc(Role role);

    List<User> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrderByIdDesc(
            String username, String displayName);

    long countByRole(Role role);

    long countByEnabledTrue();

    long countByEnabledFalse();

    Optional<User> findFirstByRoleOrderByIdAsc(Role role);
}