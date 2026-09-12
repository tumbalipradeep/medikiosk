package in.devmedi.kiosk.module.document.findings.normalize;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deterministic numeric-value normaliser.
 *
 * <p>Indian lab reports commonly print thousands separators ({@code 6,500})
 * while a few use a comma as the decimal mark ({@code 13,5}). These are
 * disambiguated by digit grouping: commas separating groups of exactly three
 * digits are thousands separators and stripped; a single comma followed by one
 * or more digits that is not a three-digit group is treated as a decimal point.
 * A leading plus sign is removed. A value is only accepted when the result is a
 * plain unsigned decimal (or an empty thousands-grouped form); anything else is
 * rejected.</p>
 */
public final class ValueNormalizer {

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("\\d+(\\.\\d+)?");
    private static final Pattern THOUSANDS_GROUPED = Pattern.compile("\\d{1,3}(,\\d{3})+(\\.\\d+)?");
    private static final Pattern COMMA_DECIMAL = Pattern.compile("\\d+,\\d+");

    private ValueNormalizer() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String t = raw.trim().replaceFirst("^\\+", "").trim();
        if (THOUSANDS_GROUPED.matcher(t).matches()) {
            t = t.replace(",", "");
        } else if (COMMA_DECIMAL.matcher(t).matches()) {
            int comma = t.indexOf(',');
            t = t.substring(0, comma) + '.' + t.substring(comma + 1);
        }
        if (!PLAIN_DECIMAL.matcher(t).matches()) {
            return Optional.empty();
        }
        return Optional.of(t);
    }
}