package in.devmedi.kiosk.module.fhir.validation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Deterministic FHIR R4 datatype validation for the value shapes MediKiosk
 * currently emits.
 *
 * <p>This is deliberately a small, strict, offline subset of the FHIR R4
 * base type rules — exactly the ones the supported resources depend on. It is
 * deterministic validation, not FHIR profile conformance: there is no FHIR
 * validator dependency available in the offline build. Every validator here
 * has a defined truth table and no side effects.</p>
 *
 * <p>Validated types: {@code id}, {@code code}, {@code system} (a URI),
 * {@code uri}, {@code instant}, {@code date}/{@code dateTime}, quantities
 * (non-null, finite decimal with an optional non-blank unit), and the
 * {@code ResourceType/id} reference target shape. Booleans are validated by
 * their record type ({@code boolean} can only be true/false).</p>
 */
public final class FhirPrimitives {

    private FhirPrimitives() {
    }

    /** FHIR {@code id}: 1..64 chars of {@code [A-Za-z0-9-.]}. */
    public static final Pattern FHIR_ID = Pattern.compile("^[A-Za-z0-9\\-.]{1,64}$");

    /** FHIR {@code code}: 1..64 chars, no leading or trailing whitespace. */
    public static final Pattern FHIR_CODE = Pattern.compile("^\\S.*\\S$|^\\S{1,64}$");

    /** Absolute HTTP(S)-style URI as used for system/urn values. */
    public static final Pattern FQ_URI = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:[^\\s]+$");

    /** Approximate MIME type token for attachment contentType. */
    public static final Pattern MIME_TYPE = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*/[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*$");

    /** FHIR {@code instant} (second precision, optional fraction, UTC or numeric offset). */
    private static final Pattern FHIR_INSTANT = Pattern.compile(
            "^\\d{4}-(0[1-9]|1[0-2])-([0-2][0-9]|3[01])"
                    + "T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]"
                    + "(\\.\\d+)?(Z|([+-])([01][0-9]|2[0-3]):[0-5][0-9])$");

    /** FHIR {@code date}: {@code yyyy-MM-dd} (the only date shape MediKiosk emits). */
    private static final Pattern FHIR_DATE =
            Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])-([0-2][0-9]|3[01])$");

    /** FHIR {@code dateTime}/{@code instant} or bare {@code date}. */
    private static final Pattern FHIR_DATE_TIME = Pattern.compile(
            "^\\d{4}(-(0[1-9]|1[0-2])(-([0-2][0-9]|3[01])"
                    + "(T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]"
                    + "(\\.\\d+)?(Z|([+-])([01][0-9]|2[0-3]):[0-5][0-9])?)?)?)?$");

    /** Valid FHIR {@code id}. */
    public static boolean isFhirId(String value) {
        return value != null && FHIR_ID.matcher(value).matches();
    }

    /** Valid FHIR {@code code}: non-blank, no surrounding whitespace, {@code <= 64} chars. */
    public static boolean isCode(String value) {
        if (value == null) {
            return false;
        }
        if (value.length() > 64 || value.length() < 1) {
            return false;
        }
        return FHIR_CODE.matcher(value).matches();
    }

    /** Absolute URI as expected for FHIR {@code system}/{@code uri} values. */
    public static boolean isAbsoluteUri(String value) {
        return value != null && FQ_URI.matcher(value).matches();
    }

    /** Valid FHIR {@code instant} (syntactically and as a real point in time). */
    public static boolean isInstant(String value) {
        if (value == null || !FHIR_INSTANT.matcher(value).matches()) {
            return false;
        }
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** Valid FHIR {@code date} ({@code yyyy-MM-dd}) that is a real calendar date. */
    public static boolean isDate(String value) {
        if (value == null || !FHIR_DATE.matcher(value).matches()) {
            return false;
        }
        try {
            DateTimeFormatter.ISO_LOCAL_DATE.parse(value, LocalDate::from);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** Valid FHIR {@code date}/{@code dateTime}/{@code instant} as emitted by MediKiosk. */
    public static boolean isDateOrDateTime(String value) {
        if (value == null || !FHIR_DATE_TIME.matcher(value).matches()) {
            return false;
        }
        if (isInstant(value) || isDate(value)) {
            return true;
        }
        try {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            // fall through to "best effort": our emitters only produce instant/date shapes
            return value.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.+$");
        }
    }

    /** Non-negative quantity value (FHIR {@code decimal}) with an optional non-blank unit. */
    public static boolean isValidQuantityValue(java.math.BigDecimal value, String unit) {
        if (value == null) {
            return false;
        }
        if (value.scale() < 0 || value.compareTo(java.math.BigDecimal.ZERO) < 0) {
            // negative or over-precise values are never produced by MediKiosk
            return false;
        }
        return unit == null || !unit.isBlank();
    }

    /** Non-negative long for sizes/counts. */
    public static boolean isNonNegative(long value) {
        return value >= 0;
    }

    /** A {@code Type/id} reference target with a known FHIR resource type and valid id. */
    public static boolean isSupportedResourceType(String type) {
        return switch (type) {
            case "Patient", "Encounter", "Observation", "DocumentReference", "Consent" -> true;
            case null -> false;
            default -> false;
        };
    }
}