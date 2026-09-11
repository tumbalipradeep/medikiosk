package in.devmedi.kiosk.module.fhir.json;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

/**
 * Central JSON serialization for the FHIR projection layer.
 *
 * <p>Uses the same Jackson 3 ({@code tools.jackson}) line as the rest of the
 * application. Model records declare their FHIR fields (e.g.
 * {@code resourceType}, {@code valueString}) verbatim, so this produces the
 * canonical FHIR JSON shape with absent elements omitted.</p>
 */
public final class FhirJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FhirJson() {
    }

    /** Serializes a FHIR model object to FHIR JSON ({@code null}s and empties omitted). */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("cannot serialize FHIR value to JSON", e);
        }
    }
}