package in.devmedi.kiosk.module.fhir.id;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Deterministic FHIR logical identifier generation.
 *
 * <p>FHIR logical ids are derived from the stable business key of the source
 * domain row (RFC 4122 UUID version 5). The same domain data therefore always
 * maps to the same resource id, on every application run, so export bundles
 * are reproducible and references between resources resolve without a
 * database identity being carried over.</p>
 *
 * <p>Id "domain keys" are {@code resourceType + '#' + canonicalKey}, where the
 * canonical key is a business key that already exists in the persisted domain,
 * for example {@code completed_cases.case_id} or {@code users.username}. The
 * resulting logical id stays within the FHIR id charset ({@code [A-Za-z0-9-]}
 * up to 64 characters).</p>
 */
public final class FhirIds {

    /** Namespace for every MediKiosk FHIR resource id. */
    public static final UUID FHIR_NAMESPACE =
            UUID.nameUUIDFromBytes("in.devmedi.kiosk.module.fhir".getBytes(StandardCharsets.UTF_8));

    private FhirIds() {
    }

    /** Deterministic logical id for a resource of {@code resourceType} with the given business key. */
    public static String logicalId(String resourceType, String domainKey) {
        return uuid5(FHIR_NAMESPACE, resourceType + "#" + domainKey).toString();
    }

    /** Bundle {@code fullUrl} for a logical id (same-bundle resolvable urn). */
    public static String fullUrl(String logicalId) {
        return "urn:uuid:" + logicalId;
    }

    /** Patient id for the {@code users.username} business key. */
    public static String patient(String username) {
        return logicalId("Patient", username);
    }

    /** Encounter id for the {@code completed_cases.case_id} business key. */
    public static String encounter(String caseId) {
        return logicalId("Encounter", caseId);
    }

    /** Answer-observation id for the case + {@code (case_id, answer_order)} unique business key. */
    public static String answer(String caseId, int answerOrder) {
        return logicalId("Observation", "answer#" + caseId + "#" + answerOrder);
    }

    /** Vital-observation id for the {@code document_id + vital_type + occurrence_index} business key. */
    public static String vital(String documentId, String vitalType, int occurrenceIndex) {
        return logicalId("Observation", "vital#" + documentId + "#" + vitalType + "#" + occurrenceIndex);
    }

    /** Lab-observation id for the {@code document_id + occurrence_index} business key. */
    public static String lab(String documentId, int occurrenceIndex) {
        return logicalId("Observation", "lab#" + documentId + "#" + occurrenceIndex);
    }

    /** DocumentReference id for the {@code clinical_documents.document_id} business key. */
    public static String document(String documentId) {
        return logicalId("DocumentReference", documentId);
    }

    /** Consent id for the {@code username + consent_type} business key. */
    public static String consent(String username, String consentType) {
        return logicalId("Consent", consentType + "#" + username);
    }

    /** RFC 4122 UUID version 5. */
    static UUID uuid5(UUID namespace, String name) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
        digest.update(toBytes(namespace));
        byte[] hash = digest.digest(name.getBytes(StandardCharsets.UTF_8));
        hash[6] = (byte) ((hash[6] & 0x0F) | 0x50);
        hash[8] = (byte) ((hash[8] & 0x3F) | 0x80);
        ByteBuffer buffer = ByteBuffer.wrap(hash);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}