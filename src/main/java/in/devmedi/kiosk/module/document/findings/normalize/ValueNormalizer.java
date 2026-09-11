package in.devmedi.kiosk.module.document.findings.normalize;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deterministic numeric-value normaliser.
 *
 * <p>Thousands separators are stripped ({@code 6,500} -&gt; {@code 6500}) and a
 * leading plus sign is removed. A value is only accepted when the result is a
 * plain unsigned decimal; anything else is rejected.</p>
 */
public final class ValueNormalizer {

    private static final Pattern DECIMAL = Pattern.compile("\\d+(\\.\\d+)?");

    private ValueNormalizer() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String t = raw.trim().replace(",", "").replaceFirst("^\\+", "").trim();
        if (!DECIMAL.matcher(t).matches()) {
            return Optional.empty();
        }
        return Optional.of(t);
    }
}