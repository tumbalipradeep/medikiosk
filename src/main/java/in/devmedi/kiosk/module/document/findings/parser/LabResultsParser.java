package in.devmedi.kiosk.module.document.findings.parser;

import in.devmedi.kiosk.module.document.findings.model.LabResult;
import in.devmedi.kiosk.module.document.findings.normalize.DateParser;
import in.devmedi.kiosk.module.document.findings.normalize.UnitNormalizer;
import in.devmedi.kiosk.module.document.findings.normalize.ValueNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic lab-results decoder.
 *
 * <p>Each line is tested against the registry's longest alias first. A line is
 * only accepted when, after the alias, a well-formed {@code value [unit]
 * [(ref-range)] [flag...]} tail is present and the remainder is fully consumed;
 * anything else is left unparsed rather than guessed.</p>
 */
public class LabResultsParser {

    private final LabTestRegistry registry;

    private static final Pattern VALUE = Pattern.compile("^\\s*([+-]?\\d+(?:[.,]\\d+)?)");
    private static final Pattern UNIT = Pattern.compile(
            "^\\s*([\\p{L}\\u00b0\\u00b2\\u00b3\\u00b5\\u03bc%^+/._]+)");
    private static final Pattern UNIT_WITH_FLAGS = Pattern.compile(
            "^([\\p{L}\\u00b0\\u00b2\\u00b3\\u00b5\\u03bc%^+/._]+)\\s*(\\([A-Za-z%]+\\s*\\)\\s*)*$");
    private static final Pattern SPECIMEN = Pattern.compile(
            "(?i)\\b(?:specimen|sample|blood\\s*drawn)\\s*(?:date|collected|drawn|taken)?\\s*[:.\\-]?\\s*"
                    + "(?<date>\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}|\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/. ]+\\p{L}{3,9}[-/., ]*\\d{2,4})");

    public LabResultsParser(LabTestRegistry registry) {
        this.registry = registry;
    }

    public List<LabResult> parse(List<String> lines) {
        List<LabResult> result = new ArrayList<>();
        for (String line : lines) {
            LabTestRegistry.Matcher matcher = matchedAlias(line);
            if (matcher == null) {
                continue;
            }
            ParsedValue parsed = parseValueTail(stripSeparator(line.substring(matcher.alias().length())));
            if (parsed == null) {
                continue;
            }
            result.add(new LabResult(matcher.canonicalName(), parsed.rawValue(), parsed.value(), parsed.unit(), parsed.range(),
                    specimenDate(line), line, result.size(), null));
        }
        return List.copyOf(result);
    }

    private LabTestRegistry.Matcher matchedAlias(String line) {
        for (LabTestRegistry.Matcher m : registry.matchers()) {
            if (startsWithIgnoreCase(line, m.alias())) {
                char after = line.length() > m.alias().length() ? line.charAt(m.alias().length()) : ' ';
                if (after == ':' || after == '=' || Character.isWhitespace(after)) {
                    return m;
                }
            }
        }
        return null;
    }

    private boolean startsWithIgnoreCase(String text, String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private String stripSeparator(String tail) {
        return tail.replaceFirst("^\\s*[:=]+\\s*", "").trim();
    }

    private ParsedValue parseValueTail(String tail) {
        Matcher vm = VALUE.matcher(tail);
        if (!vm.find()) {
            return null;
        }
        String value = vm.group(1);
        String restAfterValue = tail.substring(vm.end());

        Matcher um = UNIT.matcher(restAfterValue);
        String unit = null;
        String rest = restAfterValue;
        if (um.lookingAt()) {
            String candidate = um.group(1).trim();
            if (isSensibleUnit(candidate)) {
                unit = UnitNormalizer.normalize(candidate);
            }
            rest = restAfterValue.substring(um.end());
        }

        String range = null;
        for (int i = 0; i < 6; i++) {
            rest = rest.trim();
            if (rest.isEmpty()) {
                break;
            }
            if (rest.startsWith("(") || rest.startsWith("[")) {
                int close = rest.indexOf(rest.charAt(0) == '(' ? ')' : ']');
                if (close < 0) {
                    return null;
                }
                String inner = rest.substring(1, close).trim();
                rest = rest.substring(close + 1);
                if (hasDigit(inner) && range == null) {
                    range = collapseWhitespace(inner);
                }
                continue;
            }
            if (unit == null) {
                Matcher uwf = UNIT_WITH_FLAGS.matcher(rest);
                if (uwf.matches()) {
                    unit = UnitNormalizer.normalize(uwf.group(1));
                    rest = "";
                    break;
                }
                return null;
            }
            return null;
        }
        if (!rest.trim().isEmpty()) {
            return null;
        }
        var normalizedValue = ValueNormalizer.normalize(value);
        if (normalizedValue.isEmpty()) {
            return null;
        }
        return new ParsedValue(normalizedValue.get(), value, unit, range);
    }

    private boolean isSensibleUnit(String candidate) {
        return candidate.length() >= 1 && candidate.matches("[\\p{L}\\u00b0\\u00b2\\u00b3\\u00b5\\u03bc%^+/._]*");
    }

    private static boolean hasDigit(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String collapseWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String specimenDate(String line) {
        Matcher m = SPECIMEN.matcher(line);
        if (m.find()) {
            return DateParser.parse(m.group("date")).orElse(null);
        }
        return null;
    }

    private record ParsedValue(String value, String rawValue, String unit, String range) {
    }
}