package in.devmedi.kiosk.module.auth.preferences;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Server-side display preferences for authenticated users. One additive
 * persistence layer beside the existing browser-local preference mechanism —
 * never a second rendering architecture: values are plain strings handed to
 * the pre-paint seed and the profile form, applied by the existing
 * data-attribute contract.
 *
 * <p>Closed value sets are enforced here; the V25 CHECK constraints are the
 * second line of defence. Anonymous users never reach this service.</p>
 */
@Service
public class DisplayPreferencesService {

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String MOTION_DYNAMIC = "dynamic";
    public static final String MOTION_STANDARD = "standard";
    public static final String MOTION_REDUCED = "reduced";
    public static final String TEXT_STANDARD = "standard";
    public static final String TEXT_LARGE = "large";
    public static final String TEXT_XLARGE = "xlarge";

    public static final Set<String> THEMES = Set.of(THEME_SYSTEM, THEME_LIGHT, THEME_DARK);
    public static final Set<String> MOTIONS = Set.of(MOTION_DYNAMIC, MOTION_STANDARD, MOTION_REDUCED);
    public static final Set<String> TEXT_SIZES = Set.of(TEXT_STANDARD, TEXT_LARGE, TEXT_XLARGE);

    /** Immutable preference snapshot used for seeding and form rendering. */
    public record DisplayPreferences(String theme, String motion, String textSize) {
        public static final DisplayPreferences DEFAULT =
                new DisplayPreferences(THEME_SYSTEM, MOTION_DYNAMIC, TEXT_STANDARD);
    }

    private final UserDisplayPreferencesRepository repository;
    private final UserRepository userRepository;

    public DisplayPreferencesService(UserDisplayPreferencesRepository repository,
                                     UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    /**
     * Returns the user's stored preferences, or defaults when none exist yet.
     * Used for form rendering and tests.
     */
    @Transactional(readOnly = true)
    public DisplayPreferences preferencesOf(Long userId) {
        return findStored(userId).orElse(DisplayPreferences.DEFAULT);
    }

    /**
     * Returns the stored preferences only when a row exists. The view seeder
     * uses this so a user who has never saved server preferences (or has none
     * by role, e.g. a fresh admin) keeps their browser-local state authoritative.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<DisplayPreferences> findStored(Long userId) {
        return repository.findRowsByUserId(userId)
                .map(rows -> new DisplayPreferences(rows.getTheme(), rows.getMotion(), rows.getTextSize()));
    }

    /**
     * Validates and upserts the user's preferences. Rejected values throw
     * {@link DisplayPreferencesValidationException}; nothing partial is saved.
     */
    @Transactional
    public DisplayPreferences update(Long userId, String theme, String motion, String textSize) {
        String normalizedTheme = requireValue(THEMES, theme, "Theme must be one of: system, light, dark.");
        String normalizedMotion = requireValue(MOTIONS, motion, "Motion must be one of: dynamic, standard, reduced.");
        String normalizedTextSize = requireValue(TEXT_SIZES, textSize,
                "Text size must be one of: standard, large, xlarge.");

        UserDisplayPreferences preferences = repository.findById(userId).orElseGet(() -> {
            User user = userRepository.getReferenceById(userId);
            return UserDisplayPreferences.create(user);
        });
        preferences.setTheme(normalizedTheme);
        preferences.setMotion(normalizedMotion);
        preferences.setTextSize(normalizedTextSize);
        repository.save(preferences);
        return new DisplayPreferences(normalizedTheme, normalizedMotion, normalizedTextSize);
    }

    private static String requireValue(Set<String> allowed, String value, String message) {
        if (value == null || !allowed.contains(value.trim())) {
            throw new DisplayPreferencesValidationException(message);
        }
        return value.trim();
    }
}
