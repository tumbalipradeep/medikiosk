package in.devmedi.kiosk.module.auth.preferences;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Data access for per-user display preferences. Read paths use a lightweight
 * projection so preference seeding never loads the managed User entity.
 */
public interface UserDisplayPreferencesRepository
        extends JpaRepository<UserDisplayPreferences, Long> {

    interface Rows {
        String getTheme();
        String getMotion();
        String getTextSize();
    }

    @Query("select p.theme as theme, p.motion as motion, p.textSize as textSize "
            + "from UserDisplayPreferences p where p.id = :userId")
    Optional<Rows> findRowsByUserId(Long userId);
}
