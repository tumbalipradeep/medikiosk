package in.devmedi.kiosk.module.clinical.ayush;

/**
 * One AYUSH Ahara-Vihara (diet and lifestyle) parameter of the patient intake
 * conversation.
 *
 * <p>These eight parameters capture a plain, patient-friendly description of
 * daily diet and lifestyle. They are descriptive information gathering only —
 * no diagnosis, dosha scoring, treatment recommendation, or medical conclusion
 * is ever derived from them.</p>
 */
public enum AharaViharaParameter {

    /** Usual food/diet pattern. */
    AHARA,
    /** Usual meal timing/pattern. */
    MEAL_PATTERN,
    /** Appetite pattern. */
    APPETITE,
    /** Usual fluid/water intake. */
    HYDRATION,
    /** Usual sleep pattern. */
    SLEEP,
    /** Usual physical activity/exercise. */
    PHYSICAL_ACTIVITY,
    /** Usual daily routine. */
    DAILY_ROUTINE,
    /** Relevant lifestyle habits. */
    HABITS
}