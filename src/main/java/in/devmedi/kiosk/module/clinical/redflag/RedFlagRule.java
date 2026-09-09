package in.devmedi.kiosk.module.clinical.redflag;

import java.util.regex.Pattern;

/**
 * One deterministic safety rule: when the normalized patient answer matches
 * {@link #pattern()}, the red flag described by this rule is returned.
 *
 * @param id       stable red-flag id
 * @param severity severity this rule can raise
 * @param title    short human-readable title
 * @param message  clinical warning message (informational only, never a diagnosis)
 * @param pattern  compiled matching pattern applied to the normalized answer
 */
public record RedFlagRule(String id,
                          RedFlagSeverity severity,
                          String title,
                          String message,
                          Pattern pattern) {

    public RedFlagRule {
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null");
        }
    }
}