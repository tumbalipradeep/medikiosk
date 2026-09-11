package in.devmedi.kiosk.module.document.findings.service;

import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.findings.StructuredFindingsResponse;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindings;
import in.devmedi.kiosk.module.document.findings.model.AbnormalityStatus;
import in.devmedi.kiosk.module.document.findings.model.CompletenessStatus;
import in.devmedi.kiosk.module.document.findings.model.EncounterMetadata;
import in.devmedi.kiosk.module.document.findings.model.InteractionAnalysisStatus;
import in.devmedi.kiosk.module.document.findings.model.LabResult;
import in.devmedi.kiosk.module.document.findings.model.Medication;
import in.devmedi.kiosk.module.document.findings.model.MedicationField;
import in.devmedi.kiosk.module.document.findings.model.PatientIdentifiers;
import in.devmedi.kiosk.module.document.findings.model.VitalSign;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Maps persisted {@link ClinicalDocumentFindings} rows back into the fluent
 * model records exposed to clients. Entities never leak out.
 */
@Component
public class StructuredFindingsMapper {

    public StructuredFindingsResponse toResponse(String documentId,
                                                 ClinicalDocumentExtraction extraction,
                                                 ClinicalDocumentFindings findings) {
        PatientIdentifiers patient = null;
        EncounterMetadata encounter = null;
        List<VitalSign> vitals = List.of();
        List<LabResult> labs = List.of();
        List<Medication> medications = List.of();
        InteractionAnalysisStatus interactionAnalysisStatus = InteractionAnalysisStatus.NOT_AVAILABLE;
        java.time.Instant createdAt = null;

        if (findings != null) {
            patient = new PatientIdentifiers(findings.getPatientName(), findings.getPatientDob(),
                    findings.getPatientSex(), findings.getPatientMrn());
            encounter = new EncounterMetadata(findings.getReportDate(), findings.getEncounterDate(),
                    findings.getFacility(), findings.getClinician(), findings.getReportType());
            vitals = findings.getVitals().stream()
                    .map(v -> new VitalSign(v.getType(), v.getValue(), v.getUnit(), v.getSourceSnippet(), v.getOccurrenceIndex()))
                    .toList();
            labs = findings.getLabResults().stream()
                    .map(l -> new LabResult(l.getTestName(), l.getRawValue(), l.getValue(), l.getUnit(), l.getReferenceRange(),
                            l.getSpecimenDate(), l.getSourceSnippet(), l.getOccurrenceIndex(),
                            statusOf(AbnormalityStatus.class, l.getAbnormalityStatus(), AbnormalityStatus.UNKNOWN)))
                    .toList();
            medications = findings.getMedications().stream()
                    .map(m -> new Medication(m.getName(), m.getStrength(), m.getDose(), m.getRoute(), m.getFrequency(),
                            m.getDuration(), m.getSourceSnippet(), m.getOccurrenceIndex(),
                            statusOf(CompletenessStatus.class, m.getCompletenessStatus(), CompletenessStatus.INCOMPLETE),
                            missingFieldsOf(m.getMissingFields()), m.getDuplicateCount()))
                    .toList();
            interactionAnalysisStatus = statusOf(InteractionAnalysisStatus.class,
                    findings.getInteractionAnalysisStatus(), InteractionAnalysisStatus.NOT_AVAILABLE);
            createdAt = findings.getCreatedAt();
        }

        return new StructuredFindingsResponse(documentId,
                extraction.getStatus(),
                extraction.getErrorCategory(),
                extraction.getErrorMessage(),
                patient,
                encounter,
                vitals,
                labs,
                medications,
                interactionAnalysisStatus,
                extraction.getExtractedAt(),
                createdAt);
    }

    private <E extends Enum<E>> E statusOf(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private List<MedicationField> missingFieldsOf(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(token -> statusOf(MedicationField.class, token, null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}