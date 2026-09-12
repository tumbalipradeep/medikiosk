package in.devmedi.kiosk.module.document.findings.analysis;

import in.devmedi.kiosk.module.document.findings.model.AbnormalityStatus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic interpreter of a laboratory value against the reference range
 * given by the document itself.
 *
 * <p>The evaluator is intentionally dumb: it only compares numbers. It never
 * supplies a reference range when the document lacks one, it never consults
 * hard-coded medical ranges, and it never turns an abnormal value into a
 * diagnosis. {@link AbnormalityStatus#UNKNOWN} means the value or the range
 * could not be interpreted confidently and no claim is made.</p>
 *
 * <p>Supported range spellings are decimal bounds separated by a hyphen or
 * en-dash (inclusive, boundary values are NORMAL) and directional bounds
 * {@code < x}, {@code <= x}, {@code > x}, {@code >= x} (the boundary is NORMAL
 * only for the inclusive {@code <=}/{@code >=} forms). Trailing unit text such
 * as {@code 1.5 - 4.5 lakh/cu.mm} is ignored.</p>
 */
public final class LabAbnormalityEvaluator {

    private static final String NUMBER = "([0-9]+(?:[.,][0-9]+)?)";

    private static final Pattern EXPRESSION =
            Pattern.compile("^([<>=]{1,2}\\s*)?" + NUMBER + "(?:\\s*-\\s*" + NUMBER + ")?.*$");

    private static final Pattern THOUSANDS_GROUPED = Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?");
    private static final Pattern COMMA_DECIMAL = Pattern.compile("\\d+,\\d+");

    private LabAbnormalityEvaluator() {
    }

    /**
     * One-sided or two-sided numeric reference range with inclusive/exclusive
     * bound semantics as they appeared in the source document.
     */
    record NumericRange(Double lower, boolean lowerInclusive, Double upper, boolean upperInclusive) {
    }

    /**
     * @param value          normalised numeric value text, or {@code null}
     * @param referenceRange reference range as printed, or {@code null}
     */
    public static AbnormalityStatus evaluate(String value, String referenceRange) {
        if (value == null || value.isBlank() || referenceRange == null || referenceRange.isBlank()) {
            return AbnormalityStatus.UNKNOWN;
        }
        Double numericValue = parseNumber(value);
        if (numericValue == null) {
            return AbnormalityStatus.UNKNOWN;
        }
        String normalizedRange = referenceRange.trim().replace('–', '-').replace('—', '-');
        Matcher expression = EXPRESSION.matcher(normalizedRange);
        if (!expression.matches()) {
            return AbnormalityStatus.UNKNOWN;
        }
        String operator = expression.group(1);
        Double first = parseNumber(expression.group(2));
        Double second = expression.group(3) == null ? null : parseNumber(expression.group(3));
        if (first == null) {
            return AbnormalityStatus.UNKNOWN;
        }

        if (second == null) {
            if (operator == null) {
                return AbnormalityStatus.UNKNOWN;
            }
            String op = operator.trim();
            if (op.equals("<")) {
                return numericValue < first ? AbnormalityStatus.NORMAL : AbnormalityStatus.HIGH;
            }
            if (op.equals("<=")) {
                return numericValue <= first ? AbnormalityStatus.NORMAL : AbnormalityStatus.HIGH;
            }
            if (op.equals(">")) {
                return numericValue > first ? AbnormalityStatus.NORMAL : AbnormalityStatus.LOW;
            }
            if (op.equals(">=")) {
                return numericValue >= first ? AbnormalityStatus.NORMAL : AbnormalityStatus.LOW;
            }
            return AbnormalityStatus.UNKNOWN;
        }

        if (Double.compare(first, second) > 0) {
            return AbnormalityStatus.UNKNOWN;
        }
        if (numericValue < first) {
            return AbnormalityStatus.LOW;
        }
        if (numericValue > second) {
            return AbnormalityStatus.HIGH;
        }
        return AbnormalityStatus.NORMAL;
    }

    /**
     * Repeated evaluation is deterministic for identical inputs.
     */
    public static AbnormalityStatus evaluateRepeated(String value, String referenceRange) {
        AbnormalityStatus first = evaluate(value, referenceRange);
        if (first != evaluate(value, referenceRange)) {
            throw new IllegalStateException("evaluation is not deterministic");
        }
        return first;
    }

    static Double parseNumber(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.trim().replaceAll("\\s+", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        if (THOUSANDS_GROUPED.matcher(cleaned).matches()) {
            cleaned = cleaned.replace(",", "");
        } else if (COMMA_DECIMAL.matcher(cleaned).matches()) {
            int comma = cleaned.indexOf(',');
            cleaned = cleaned.substring(0, comma) + '.' + cleaned.substring(comma + 1);
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}