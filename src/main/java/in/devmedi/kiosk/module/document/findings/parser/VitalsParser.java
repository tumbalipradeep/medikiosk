package in.devmedi.kiosk.module.document.findings.parser;

import in.devmedi.kiosk.module.document.findings.model.VitalSign;
import in.devmedi.kiosk.module.document.findings.model.VitalType;
import in.devmedi.kiosk.module.document.findings.normalize.ValueNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic decoder of vital signs. Rules are evaluated in a fixed order
 * per line and occurrences are numbered in document order. A unit is only
 * recorded when the source states one - except for blood pressure, where the
 * systolic/diastolic pair format itself implies mmHg.
 */
public class VitalsParser {

    private static final Pattern BP = Pattern.compile(
            "(?i)\\b(?:blood\\s*pressure|b\\.?\\s*p\\.?)\\s*[:.\\-]?\\s*(\\d{2,3})\\s*/\\s*(\\d{2,3})\\b(?:\\s*(mm\\s*hg|mmhg))?");
    private static final Pattern HEART_RATE = Pattern.compile(
            "(?i)\\b(pulse\\s*rate|heart\\s*rate|pulse)\\s*[:.\\-]?\\s*(\\d{2,3})\\b"
                    + "(?:\\s*(beats?\\s*per\\s*minute|beats?\\s*/\\s*min|bpm|per\\s*minute|/\\s*min))?");
    private static final Pattern RESPIRATORY_RATE = Pattern.compile(
            "(?i)\\b(respiratory\\s*rate|respiration\\s*rate|resp\\.?\\s*rate|rr)\\s*[:.\\-]?\\s*(\\d{1,3})\\b"
                    + "(?:\\s*(breaths?\\s*per\\s*minute|breaths?\\s*/\\s*min|bpm|per\\s*minute|/\\s*min))?");
    private static final Pattern TEMPERATURE = Pattern.compile(
            "(?i)\\btemp(?:erature)?\\s*[:.\\-]?\\s*(\\d{2,3}(?:[.,]\\d{1,2})?)"
                    + "\\s*(?:(\\u00B0)\\s*([FC])|([FC])(?:elsius|ahrenheit)?|deg(?:rees?|\\.)?\\s*([FC])(?:elsius|ahrenheit)?)?");
    private static final Pattern OXYGEN_SATURATION = Pattern.compile(
            "(?i)\\b(spo2|spo\\u2082|oxygen\\s*saturation|o2\\s*sat(?:uration)?|pulse\\s*ox(?:imetry)?)\\s*[:.\\-]?\\s*(\\d{1,3})\\s*(%)?");
    private static final Pattern HEIGHT = Pattern.compile(
            "(?i)\\bheight\\s*[:.\\-]?\\s*(\\d+(?:[.,]\\d+)?)\\s*(cm|m|ft|feet|foot|inch|inches|in)?");
    private static final Pattern WEIGHT = Pattern.compile(
            "(?i)\\bweight\\s*[:.\\-]?\\s*(\\d+(?:[.,]\\d+)?)\\s*(kg|kgs|g|grams?|lbs?|pound|pounds)?");
    private static final Pattern BMI = Pattern.compile(
            "(?i)\\b(?:bmi|body\\s*mass\\s*index)\\s*[:.\\-]?\\s*(\\d+(?:[.,]\\d+)?)\\s*(kg/m2|kg/m\\u00B2)?");

    private record Rule(VitalType type, Pattern pattern) {
    }

    private final List<Rule> rules = List.of(
            new Rule(VitalType.HEIGHT, HEIGHT),
            new Rule(VitalType.WEIGHT, WEIGHT),
            new Rule(VitalType.BMI, BMI),
            new Rule(VitalType.BLOOD_PRESSURE, BP),
            new Rule(VitalType.HEART_RATE, HEART_RATE),
            new Rule(VitalType.RESPIRATORY_RATE, RESPIRATORY_RATE),
            new Rule(VitalType.TEMPERATURE, TEMPERATURE),
            new Rule(VitalType.OXYGEN_SATURATION, OXYGEN_SATURATION));

    public List<VitalSign> parse(List<String> lines) {
        List<VitalSign> result = new ArrayList<>();
        int occurrence = 0;
        for (String line : lines) {
            for (Rule rule : rules) {
                Matcher m = rule.pattern().matcher(line);
                if (m.find()) {
                    VitalSign sign = decode(rule.type(), m, line);
                    if (sign != null) {
                        result.add(new VitalSign(sign.type(), sign.value(), sign.unit(), line, occurrence++));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private VitalSign decode(VitalType type, Matcher m, String line) {
        return switch (type) {
            case BLOOD_PRESSURE -> {
                String value = m.group(1) + "/" + m.group(2);
                yield new VitalSign(VitalType.BLOOD_PRESSURE, value, "mmHg", line, 0);
            }
            case HEART_RATE -> vital(type, m.group(2), m.group(3));
            case RESPIRATORY_RATE -> vital(type, m.group(2), m.group(3));
            case TEMPERATURE -> {
                String value = normalizeDecimal(m.group(1));
                String unit = temperatureUnit(m);
                yield new VitalSign(VitalType.TEMPERATURE, value, unit, line, 0);
            }
            case OXYGEN_SATURATION -> {
                String value = m.group(2);
                String unit = m.group(3) != null ? "%" : null;
                yield new VitalSign(VitalType.OXYGEN_SATURATION, value, unit, line, 0);
            }
            case HEIGHT -> vital(type, m.group(1), m.group(2));
            case WEIGHT -> vital(type, m.group(1), m.group(2));
            case BMI -> vital(type, m.group(1), m.group(2));
        };
    }

    private static VitalSign vital(VitalType type, String rawValue, String rawUnit) {
        var value = ValueNormalizer.normalize(normalizeDecimal(rawValue));
        if (value.isEmpty()) {
            return null;
        }
        String unit = rawUnit != null && !rawUnit.isBlank()
                ? in.devmedi.kiosk.module.document.findings.normalize.UnitNormalizer.normalize(rawUnit)
                : null;
        return new VitalSign(type, value.get(), unit, "", 0);
    }

    private static String temperatureUnit(Matcher m) {
        if (m.group(2) != null && m.group(3) != null) {
            return "\u00B0" + m.group(3).toUpperCase();
        }
        if (m.group(4) != null) {
            return "\u00B0" + m.group(4).toUpperCase();
        }
        if (m.group(5) != null) {
            return "\u00B0" + m.group(5).toUpperCase();
        }
        return null;
    }

    private static String normalizeDecimal(String raw) {
        return raw.replace(",", ".");
    }
}