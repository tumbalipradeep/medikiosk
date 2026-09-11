package in.devmedi.kiosk.module.document.findings.normalize;

import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deterministic date decoding used across the findings parsers.
 *
 * <p>Supported shapes:</p>
 * <ul>
 *   <li>{@code dd/mm/yyyy}, {@code dd-mm-yyyy}, {@code dd.mm.yyyy} (day-first) and the
 *       2-digit-year variants {@code dd/mm/yy}</li>
 *   <li>ISO {@code yyyy-mm-dd}</li>
 *   <li>named months: {@code dd Mon yyyy}, {@code d MMMM, yyyy}</li>
 * </ul>
 *
 * <p>Rule on ambiguity: when a path is genuinely ambiguous the parser never
 * guesses - {@code 05/06/2024} is day-first ({@code 05/06/2024}) and a two-digit
 * year is preserved as written ({@code 05/06/24}) rather than expanded.</p>
 */
public final class DateParser {

    private static final Pattern ISO =
            Pattern.compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern NUMERIC =
            Pattern.compile("(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})");
    private static final Pattern NAMED =
            Pattern.compile("(\\d{1,2})[-/., ]+(\\p{L}{3,9})[-/., ]+(\\d{2,4})");

    private static final Map<String, String> SHORT_MONTHS = Map.ofEntries(
            Map.entry("jan", "Jan"), Map.entry("january", "Jan"),
            Map.entry("feb", "Feb"), Map.entry("february", "Feb"),
            Map.entry("mar", "Mar"), Map.entry("march", "Mar"),
            Map.entry("apr", "Apr"), Map.entry("april", "Apr"),
            Map.entry("may", "May"),
            Map.entry("jun", "Jun"), Map.entry("june", "Jun"),
            Map.entry("jul", "Jul"), Map.entry("july", "Jul"),
            Map.entry("aug", "Aug"), Map.entry("august", "Aug"),
            Map.entry("sep", "Sep"), Map.entry("sept", "Sep"), Map.entry("september", "Sep"),
            Map.entry("oct", "Oct"), Map.entry("october", "Oct"),
            Map.entry("nov", "Nov"), Map.entry("november", "Nov"),
            Map.entry("dec", "Dec"), Map.entry("december", "Dec"));

    private DateParser() {
    }

    /**
     * Parses a raw date fragment to a canonical string, or returns empty when
     * it is not a recognised date.
     */
    public static Optional<String> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return Optional.empty();
        }
        var iso = ISO.matcher(t);
        if (iso.matches()) {
            return Optional.of(fmt(iso.group(3), iso.group(2), iso.group(1)));
        }
        var named = NAMED.matcher(t);
        if (named.matches()) {
            String month = normalizeMonth(named.group(2));
            if (month == null) {
                return Optional.empty();
            }
            String day = String.format(Locale.ROOT, "%02d", Integer.parseInt(named.group(1)));
            return Optional.of(day + " " + month + " " + named.group(3));
        }
        var num = NUMERIC.matcher(t);
        if (num.matches()) {
            return Optional.of(fmt(num.group(1), num.group(2), num.group(3)));
        }
        return Optional.empty();
    }

    /** Day-first canonicalisation. Two-digit years are kept as written. */
    private static String fmt(String day, String month, String year) {
        String dd = String.format(Locale.ROOT, "%02d", Integer.parseInt(day));
        String mm = String.format(Locale.ROOT, "%02d", Integer.parseInt(month));
        String yyyy = year.length() == 4 ? year : String.format(Locale.ROOT, "%02d", Integer.parseInt(year));
        return dd + "/" + mm + "/" + yyyy;
    }

    private static String normalizeMonth(String month) {
        return SHORT_MONTHS.get(month.trim().toLowerCase(Locale.ROOT));
    }
}