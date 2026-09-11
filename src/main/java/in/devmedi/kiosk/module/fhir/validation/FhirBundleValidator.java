package in.devmedi.kiosk.module.fhir.validation;

import in.devmedi.kiosk.module.fhir.model.FhirBundle;
import in.devmedi.kiosk.module.fhir.model.FhirCodeableConcept;
import in.devmedi.kiosk.module.fhir.model.FhirCoding;
import in.devmedi.kiosk.module.fhir.model.FhirConsent;
import in.devmedi.kiosk.module.fhir.model.FhirContent;
import in.devmedi.kiosk.module.fhir.model.FhirDocumentReference;
import in.devmedi.kiosk.module.fhir.model.FhirEncounter;
import in.devmedi.kiosk.module.fhir.model.FhirEntry;
import in.devmedi.kiosk.module.fhir.model.FhirObservation;
import in.devmedi.kiosk.module.fhir.model.FhirPatient;
import in.devmedi.kiosk.module.fhir.model.FhirPeriod;
import in.devmedi.kiosk.module.fhir.model.FhirQuantity;
import in.devmedi.kiosk.module.fhir.model.FhirReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic structural validation for an assembled FHIR R4 {@code Bundle}.
 *
 * <p>This is the strongest validation possible without a FHIR R4 validator
 * dependency (none is resolvable offline): it checks, purely from the
 * in-memory model, that (1) the bundle is a {@code collection} with a stable
 * {@code id} and a FHIR {@code instant} timestamp, (2) every entry has a
 * {@code urn:uuid} {@code fullUrl} that exactly matches its resource id,
 * (3) resource ids are unique and valid FHIR ids, (4) every internal
 * {@code Reference} has the form {@code <Type>/<id>}, resolves to a bundle
 * entry, and targets the right resource type, (5) required elements and
 * datatype rules (from {@link FhirPrimitives}) hold for every supported
 * resource type, and (6) the three Observation representations follow their
 * mapping invariants (question/vital/lab encoding, value XOR, interpretation
 * only L/H/N for labs, provenance notes, metadata-only documents). Optional
 * clinical data (answers without a value, findings without a report type,
 * metadata-only documents, ownerless encounters, missing consents) is never
 * mis-reported. This is deterministic structural validation, not FHIR R4 /
 * profile conformance validation.</p>
 */
@Component
public class FhirBundleValidator {

    private static final String INTERPRETATION_SYSTEM =
            "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";
    private static final Set<String> INTERPRETATION_CODES = Set.of("L", "H", "N");
    private static final Set<String> KNOWN_OBSERVATION_SYSTEMS = Set.of(
            "urn:medikiosk:question-code", "urn:medikiosk:vital-type", "urn:medikiosk:lab-test");

    /** Validates the bundle deterministically; returns issues (empty when structurally consistent). */
    public List<String> validate(FhirBundle bundle) {
        List<String> issues = new ArrayList<>();
        if (bundle == null) {
            issues.add("bundle is null");
            return issues;
        }
        if (!"Bundle".equals(bundle.resourceType())) {
            issues.add("bundle.resourceType is not Bundle: " + bundle.resourceType());
        }
        if (!"collection".equals(bundle.type())) {
            issues.add("bundle.type is not collection: " + bundle.type());
        }
        if (!FhirPrimitives.isFhirId(bundle.id())) {
            issues.add("bundle.id is not a FHIR id: " + bundle.id());
        }
        if (!FhirPrimitives.isInstant(bundle.timestamp())) {
            issues.add("bundle.timestamp is not a FHIR instant: " + bundle.timestamp());
        }
        if (bundle.entry() == null) {
            issues.add("bundle.entry is missing");
            return issues;
        }

        Set<String> ids = new HashSet<>();
        Map<String, String> typeById = new HashMap<>();
        for (FhirEntry entry : bundle.entry()) {
            if (entry == null) {
                issues.add("entry is null");
                continue;
            }
            validateEntryShape(issues, entry);
            if (entry.resource() == null) {
                issues.add("entry has no resource");
                continue;
            }
            String fullUrl = entry.fullUrl();
            String logicalId = fullUrl == null ? null
                    : fullUrl.startsWith("urn:uuid:") ? fullUrl.substring("urn:uuid:".length()) : null;
            String type = resourceType(entry.resource());
            if (type == null) {
                issues.add("unsupported resource class: " + entry.resource().getClass());
                continue;
            }
            if (!type.equals(declaredResourceType(entry.resource()))) {
                issues.add("resourceType mismatch for " + type + "/" + logicalId
                        + ": id-shape '" + declaredResourceType(entry.resource()) + "'");
            }
            if (logicalId != null && !logicalId.equals(resourceId(entry.resource()))) {
                issues.add("fullUrl does not match resource id for " + type + "/"
                        + resourceId(entry.resource()));
            }
            if (!ids.add(logicalId)) {
                issues.add("duplicate resource id in bundle: " + type + "/" + logicalId);
            }
            typeById.put(logicalId, type);
            validateRequired(issues, type, entry.resource());
        }

        for (FhirEntry entry : bundle.entry()) {
            if (entry == null || entry.resource() == null) {
                continue;
            }
            String logicalId = entry.fullUrl() == null ? null
                    : entry.fullUrl().startsWith("urn:uuid:")
                    ? entry.fullUrl().substring("urn:uuid:".length()) : null;
            collectReferences(entry.resource()).forEach(reference ->
                    validateReference(issues, resourceType(entry.resource()) + "/" + logicalId,
                            reference, typeById));
        }
        return issues;
    }

    private void validateEntryShape(List<String> issues, FhirEntry entry) {
        String fullUrl = entry.fullUrl();
        if (fullUrl == null || !fullUrl.startsWith("urn:uuid:")) {
            issues.add("entry.fullUrl is not a urn:uuid: " + fullUrl);
            return;
        }
        String logicalId = fullUrl.substring("urn:uuid:".length());
        if (!FhirPrimitives.isFhirId(logicalId)) {
            issues.add("entry.fullUrl id is not a FHIR id: " + logicalId);
        }
    }

    private void validateRequired(List<String> issues, String type, Object resource) {
        switch (type) {
            case "Patient" -> validatePatient(issues, (FhirPatient) resource);
            case "Encounter" -> validateEncounter(issues, (FhirEncounter) resource);
            case "Observation" -> validateObservation(issues, (FhirObservation) resource);
            case "DocumentReference" -> validateDocumentReference(issues, (FhirDocumentReference) resource);
            case "Consent" -> validateConsent(issues, (FhirConsent) resource);
            default -> {
            }
        }
    }

    private void validatePatient(List<String> issues, FhirPatient patient) {
        String id = patient.id();
        if (!FhirPrimitives.isFhirId(id)) {
            issues.add("Patient id is not a FHIR id: " + id);
        }
        if ((patient.identifier() == null || patient.identifier().isEmpty())
                && (patient.name() == null || patient.name().isEmpty())) {
            issues.add("Patient has neither identifier nor name");
        }
        if (patient.identifier() != null) {
            patient.identifier().forEach(identifier -> {
                if (identifier.system() != null && !FhirPrimitives.isAbsoluteUri(identifier.system())) {
                    issues.add("Patient identifier system is not an absolute URI: "
                            + identifier.system());
                }
                if (!FhirPrimitives.isCode(identifier.value())) {
                    issues.add("Patient identifier value is not a valid code: " + identifier.value());
                }
            });
        }
        if (patient.name() != null) {
            patient.name().forEach(name -> {
                if (name.text() == null || name.text().isBlank()) {
                    issues.add("Patient name has no text");
                }
            });
        }
    }

    private void validateEncounter(List<String> issues, FhirEncounter encounter) {
        if (!FhirPrimitives.isFhirId(encounter.id())) {
            issues.add("Encounter id is not a FHIR id: " + encounter.id());
        }
        if (!"finished".equals(encounter.status())) {
            issues.add("Encounter status is not finished: " + encounter.status());
        }
        if (encounter.clazz() == null
                || !containsCoding(encounter.clazz(),
                "http://terminology.hl7.org/CodeSystem/v3-ActCode", "AMB")) {
            issues.add("Encounter class coding must be v3-ActCode AMB");
        }
        validatePeriod(issues, "Encounter.period", encounter.period());
    }

    private void validateObservation(List<String> issues, FhirObservation observation) {
        if (!FhirPrimitives.isFhirId(observation.id())) {
            issues.add("Observation id is not a FHIR id: " + observation.id());
        }
        if (!"final".equals(observation.status())) {
            issues.add("Observation status is not final: " + observation.status());
        }
        if (observation.code() == null) {
            issues.add("Observation has no code: " + observation.id());
            return;
        }
        validateCodeableConcept(issues, "Observation.code", observation.code());

        long recognized = observation.code().coding() == null ? 0
                : observation.code().coding().stream()
                        .map(FhirCoding::system)
                        .filter(KNOWN_OBSERVATION_SYSTEMS::contains)
                        .count();
        if (recognized == 0) {
            issues.add("Observation code has no known MediKiosk system: " + observation.id());
        }
        boolean question = hasSystem(observation.code(), "urn:medikiosk:question-code");
        boolean vital = hasSystem(observation.code(), "urn:medikiosk:vital-type");
        boolean lab = hasSystem(observation.code(), "urn:medikiosk:lab-test");

        boolean hasString = observation.valueString() != null;
        boolean hasQuantity = observation.valueQuantity() != null;
        if (hasString && hasQuantity) {
            issues.add("Observation has both valueString and valueQuantity: " + observation.id());
        }
        if (observation.interpretation() != null && !observation.interpretation().isEmpty()) {
            if (!lab) {
                issues.add("Observation interpretation is only allowed on lab findings: "
                        + observation.id());
            } else {
                observation.interpretation().forEach(concept -> {
                    if (concept.coding() == null || concept.coding().stream()
                            .anyMatch(coding -> !isLabInterpretation(coding))) {
                        issues.add("Observation interpretation must be L/H/N with system "
                                + INTERPRETATION_SYSTEM + ": " + observation.id());
                    }
                });
            }
        }
        if (hasQuantity) {
            FhirQuantity quantity = observation.valueQuantity();
            if (!FhirPrimitives.isValidQuantityValue(quantity.value(), quantity.unit())) {
                issues.add("Observation has an invalid quantity: " + observation.id());
            }
        }
        if (question && observation.note() != null) {
            boolean provenance = observation.note().stream().anyMatch(note ->
                    note.text() != null && note.text().contains("answerSource=")
                            && note.text().contains("language="));
            if (!provenance) {
                issues.add("Answer observation lacks answerSource/language provenance: "
                        + observation.id());
            }
        }
        if ((vital || lab) && (observation.note() == null || observation.note().isEmpty())) {
            issues.add("Finding observation lacks a source-snippet note: " + observation.id());
        }
    }

    private boolean isLabInterpretation(FhirCoding coding) {
        return coding != null
                && INTERPRETATION_SYSTEM.equals(coding.system())
                && INTERPRETATION_CODES.contains(coding.code());
    }

    private void validateDocumentReference(List<String> issues, FhirDocumentReference reference) {
        if (!FhirPrimitives.isFhirId(reference.id())) {
            issues.add("DocumentReference id is not a FHIR id: " + reference.id());
        }
        if (!"current".equals(reference.status())) {
            issues.add("DocumentReference status is not current: " + reference.status());
        }
        if (reference.type() != null) {
            validateCodeableConcept(issues, "DocumentReference.type", reference.type());
        }
        if (reference.date() != null && !FhirPrimitives.isDateOrDateTime(reference.date())) {
            issues.add("DocumentReference date is not a FHIR date/dateTime: " + reference.date());
        }
        if (reference.content() == null || reference.content().isEmpty()) {
            issues.add("DocumentReference has no content: " + reference.id());
            return;
        }
        for (FhirContent content : reference.content()) {
            if (content.attachment() == null) {
                issues.add("DocumentReference content has no attachment: " + reference.id());
                continue;
            }
            var attachment = content.attachment();
            if (!FhirPrimitives.MIME_TYPE.matcher(attachment.contentType() == null ? ""
                    : attachment.contentType()).matches()) {
                issues.add("DocumentReference attachment contentType is not a MIME type: "
                        + attachment.contentType());
            }
            if (!FhirPrimitives.isNonNegative(attachment.size())) {
                issues.add("DocumentReference attachment size is negative: " + attachment.size());
            }
            if (attachment.title() == null || attachment.title().isBlank()) {
                issues.add("DocumentReference attachment has no title: " + reference.id());
            }
        }
        if (reference.context() != null
                && (reference.context().encounter() == null
                || reference.context().encounter().isEmpty())) {
            issues.add("DocumentReference context has no encounter: " + reference.id());
        }
    }

    private void validateConsent(List<String> issues, FhirConsent consent) {
        if (!FhirPrimitives.isFhirId(consent.id())) {
            issues.add("Consent id is not a FHIR id: " + consent.id());
        }
        if (consent.status() == null
                || (!consent.status().equals("active") && !consent.status().equals("inactive"))) {
            issues.add("Consent status must be active or inactive: " + consent.status());
        }
        if (consent.scope() == null
                || !containsCoding(consent.scope(),
                "http://terminology.hl7.org/CodeSystem/consentscope", "patient-privacy")) {
            issues.add("Consent scope must be patient-privacy");
        }
        if (consent.category() == null || consent.category().isEmpty()) {
            issues.add("Consent has no category: " + consent.id());
        } else {
            consent.category().forEach(concept ->
                    validateSpecificCoding(issues, "Consent.category", concept,
                            "urn:medikiosk:consent-type"));
        }
        if (consent.dateTime() != null && !FhirPrimitives.isInstant(consent.dateTime())) {
            issues.add("Consent dateTime is not a FHIR instant: " + consent.dateTime());
        }
        if (consent.provision() == null) {
            issues.add("Consent has no provision: " + consent.id());
        } else {
            String type = consent.provision().type();
            if (!"permit".equals(type) && !"deny".equals(type)) {
                issues.add("Consent provision type must be permit or deny: " + type);
            }
            validatePeriod(issues, "Consent.provision.period", consent.provision().period());
            if (consent.provision().purpose() != null) {
                consent.provision().purpose().forEach(concept -> {
                    if (concept.text() == null || concept.text().isBlank()) {
                        issues.add("Consent provision purpose has no text: " + consent.id());
                    }
                });
            }
        }
    }

    private void validatePeriod(List<String> issues, String path, FhirPeriod period) {
        if (period == null) {
            return;
        }
        if (period.start() != null && !FhirPrimitives.isInstant(period.start())) {
            issues.add(path + " start is not a FHIR instant: " + period.start());
        }
        if (period.end() != null && !FhirPrimitives.isInstant(period.end())) {
            issues.add(path + " end is not a FHIR instant: " + period.end());
        }
    }

    private void validateCodeableConcept(List<String> issues, String path, FhirCodeableConcept concept) {
        if (concept.coding() == null || concept.coding().isEmpty()) {
            if (concept.text() == null || concept.text().isBlank()) {
                issues.add(path + " has neither coding nor text");
            }
            return;
        }
        for (FhirCoding coding : concept.coding()) {
            if (!FhirPrimitives.isCode(coding.code())) {
                issues.add(path + " coding has an invalid code: " + coding.code());
            }
            if (coding.system() != null && !FhirPrimitives.isAbsoluteUri(coding.system())) {
                issues.add(path + " coding system is not an absolute URI: " + coding.system());
            }
        }
    }

    private void validateSpecificCoding(List<String> issues, String path,
                                        FhirCodeableConcept concept, String expectedSystem) {
        if (concept.coding() == null || concept.coding().isEmpty()
                || concept.coding().stream().anyMatch(coding -> !expectedSystem.equals(coding.system()))) {
            issues.add(path + " coding must use system " + expectedSystem);
        } else {
            validateCodeableConcept(issues, path, concept);
        }
    }

    private boolean hasSystem(FhirCodeableConcept concept, String system) {
        return concept.coding() != null
                && concept.coding().stream().anyMatch(coding -> system.equals(coding.system()));
    }

    private boolean containsCoding(FhirCodeableConcept concept, String system, String code) {
        return concept.coding() != null
                && concept.coding().stream()
                .anyMatch(coding -> system.equals(coding.system()) && code.equals(coding.code()));
    }

    private void validateReference(List<String> issues, String source,
                                   FhirReference reference, Map<String, String> typeById) {
        if (reference == null || reference.reference() == null) {
            return;
        }
        String target = reference.reference();
        int slash = target.indexOf('/');
        if (slash <= 0) {
            issues.add("malformed reference '" + target + "' in " + source);
            return;
        }
        String targetType = target.substring(0, slash);
        String targetId = target.substring(slash + 1);
        if (!FhirPrimitives.isSupportedResourceType(targetType)) {
            issues.add("reference to unsupported resource type '" + target + "' in " + source);
            return;
        }
        if (!FhirPrimitives.isFhirId(targetId)) {
            issues.add("reference target id is not a FHIR id '" + target + "' in " + source);
        }
        String resolvedType = typeById.get(targetId);
        if (resolvedType == null) {
            issues.add("unresolved reference '" + target + "' in " + source);
        } else if (!resolvedType.equals(targetType)) {
            issues.add("reference type mismatch '" + target + "' in " + source
                    + " (bundle holds " + resolvedType + ")");
        }
    }

    private List<FhirReference> collectReferences(Object resource) {
        List<FhirReference> refs = new ArrayList<>();
        if (resource instanceof FhirEncounter encounter) {
            addIfNotNull(refs, encounter.subject());
        } else if (resource instanceof FhirObservation observation) {
            addIfNotNull(refs, observation.subject());
            addIfNotNull(refs, observation.encounter());
        } else if (resource instanceof FhirDocumentReference reference) {
            if (reference.context() != null && reference.context().encounter() != null) {
                reference.context().encounter().forEach(candidate -> addIfNotNull(refs, candidate));
            }
        } else if (resource instanceof FhirConsent consent) {
            addIfNotNull(refs, consent.patient());
        }
        return refs;
    }

    private void addIfNotNull(List<FhirReference> refs, FhirReference reference) {
        if (reference != null) {
            refs.add(reference);
        }
    }

    /** FHIR resource type implied by the model class. */
    private String resourceType(Object resource) {
        return switch (resource) {
            case FhirPatient ignored -> "Patient";
            case FhirEncounter ignored -> "Encounter";
            case FhirObservation ignored -> "Observation";
            case FhirDocumentReference ignored -> "DocumentReference";
            case FhirConsent ignored -> "Consent";
            default -> null;
        };
    }

    /** FHIR resource id, or {@code null} for an unsupported model class. */
    private static String resourceId(Object resource) {
        return switch (resource) {
            case FhirPatient p -> p.id();
            case FhirEncounter e -> e.id();
            case FhirObservation o -> o.id();
            case FhirDocumentReference d -> d.id();
            case FhirConsent c -> c.id();
            default -> null;
        };
    }

    /** FHIR {@code resourceType} string declared by the model class. */
    private static String declaredResourceType(Object resource) {
        return switch (resource) {
            case FhirPatient p -> p.resourceType();
            case FhirEncounter e -> e.resourceType();
            case FhirObservation o -> o.resourceType();
            case FhirDocumentReference d -> d.resourceType();
            case FhirConsent c -> c.resourceType();
            default -> null;
        };
    }
}