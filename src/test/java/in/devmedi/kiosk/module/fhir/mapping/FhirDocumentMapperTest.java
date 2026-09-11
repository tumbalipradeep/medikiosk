package in.devmedi.kiosk.module.fhir.mapping;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.extraction.ExtractionErrorCategory;
import in.devmedi.kiosk.module.document.extraction.ExtractionOutcome;
import in.devmedi.kiosk.module.document.extraction.ExtractionResult;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindings;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindingsLab;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindingsVital;
import in.devmedi.kiosk.module.document.findings.model.AbnormalityStatus;
import in.devmedi.kiosk.module.document.findings.model.InteractionAnalysisStatus;
import in.devmedi.kiosk.module.document.findings.model.LabResult;
import in.devmedi.kiosk.module.document.findings.model.PatientIdentifiers;
import in.devmedi.kiosk.module.document.findings.model.StructuredFindings;
import in.devmedi.kiosk.module.document.findings.model.VitalSign;
import in.devmedi.kiosk.module.document.findings.model.VitalType;
import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.model.FhirDocumentReference;
import in.devmedi.kiosk.module.fhir.model.FhirObservation;
import in.devmedi.kiosk.module.fhir.model.FhirQuantity;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FhirDocumentMapperTest {

    private static final FhirContextRefs REFS =
            new FhirContextRefs("Patient/" + FhirIds.patient("patient7"),
                    "Encounter/" + FhirIds.encounter("case-1"));

    private ClinicalDocument document;
    private ClinicalDocumentFindings findings;

    @BeforeEach
    void setUp() {
        User user = new User("patient7", "secret", "Priya Sharma", Role.PATIENT);
        CompletedCaseEntity completedCase = new CompletedCaseEntity("case-1");
        document = ClinicalDocument.create("doc-abc", completedCase, user,
                "lab-report.pdf", "stored.pdf", "application/pdf", 2048);

        ClinicalDocumentExtraction extraction = ClinicalDocumentExtraction.create(document,
                new ExtractionOutcome(ExtractionStatus.EXTRACTED, ExtractionErrorCategory.NONE, null,
                        "report text", 1, List.of(new ExtractionResult.PageText(1, "report text"))));
        StructuredFindings structured = new StructuredFindings(
                new PatientIdentifiers("Ravi Kumar", null, "Male", "MRN123"),
                null,
                List.of(new VitalSign(VitalType.BLOOD_PRESSURE, "128/82", "mmHg",
                        "BP: 128/82 mmHg", 0)),
                List.of(new LabResult("Hemoglobin", "14.2 g/dL", "14.2", "g/dL",
                        "13.0 - 17.0", null, "Hb: 14.2 g/dL", 0, AbnormalityStatus.NORMAL)),
                List.of(), InteractionAnalysisStatus.NOT_AVAILABLE);
        findings = ClinicalDocumentFindings.create(extraction, structured);
    }

    @Test
    void mapsDocumentReferenceWithAttachmentAndEncounterContext() {
        FhirDocumentReference reference = FhirDocumentMapper.toDocumentReference(
                document, "Lab Report", REFS.encounterReference());

        assertThat(reference.resourceType()).isEqualTo("DocumentReference");
        assertThat(reference.id()).isEqualTo(FhirIds.document("doc-abc"));
        assertThat(reference.status()).isEqualTo("current");
        assertThat(reference.date()).isNull();
        assertThat(reference.type().coding()).hasSize(1);
        assertThat(reference.type().coding().getFirst().system()).isEqualTo("urn:medikiosk:report-type");
        assertThat(reference.type().coding().getFirst().code()).isEqualTo("Lab Report");
        assertThat(reference.content()).hasSize(1);
        assertThat(reference.content().getFirst().attachment().contentType()).isEqualTo("application/pdf");
        assertThat(reference.content().getFirst().attachment().size()).isEqualTo(2048);
        assertThat(reference.content().getFirst().attachment().title()).isEqualTo("lab-report.pdf");
        assertThat(reference.context().encounter().getFirst().reference())
                .isEqualTo(REFS.encounterReference());
    }

    @Test
    void documentReferenceWithoutReportTypeHasNoTypeCoding() {
        FhirDocumentReference reference = FhirDocumentMapper.toDocumentReference(
                document, null, REFS.encounterReference());
        assertThat(reference.type()).isNull();
    }

    @Test
    void compositeVitalValueIsValueStringNotQuantity() {
        FhirObservation observation = FhirDocumentMapper.toVitalObservation(
                document, findings.getVitals().getFirst(), REFS);

        assertThat(observation.id()).isEqualTo(FhirIds.vital("doc-abc", "BLOOD_PRESSURE", 0));
        assertThat(observation.code().coding().getFirst().system()).isEqualTo("urn:medikiosk:vital-type");
        assertThat(observation.code().coding().getFirst().code()).isEqualTo("BLOOD_PRESSURE");
        assertThat(observation.code().coding().getFirst().display()).isEqualTo("Blood pressure");
        assertThat(observation.valueString()).isEqualTo("128/82");
        assertThat(observation.valueQuantity()).isNull();
        assertThat(observation.note().getFirst().text()).isEqualTo("BP: 128/82 mmHg");
        assertThat(observation.subject().reference()).isEqualTo(REFS.subjectReference());
    }

    @Test
    void numericLabValueIsQuantityWithNormalInterpretation() {
        ClinicalDocumentFindingsLab lab = findings.getLabResults().getFirst();
        FhirObservation observation = FhirDocumentMapper.toLabObservation(document, lab, REFS);

        assertThat(observation.id()).isEqualTo(FhirIds.lab("doc-abc", 0));
        assertThat(observation.code().coding().getFirst().system()).isEqualTo("urn:medikiosk:lab-test");
        assertThat(observation.code().coding().getFirst().code()).isEqualTo("Hemoglobin");
        assertThat(observation.valueString()).isNull();
        assertThat(observation.valueQuantity()).isNotNull();
        assertThat(observation.valueQuantity().value()).isEqualByComparingTo("14.2");
        assertThat(observation.valueQuantity().unit()).isEqualTo("g/dL");
        assertThat(observation.interpretation()).hasSize(1);
        assertThat(observation.interpretation().getFirst().coding().getFirst().system())
                .isEqualTo("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation");
        assertThat(observation.interpretation().getFirst().coding().getFirst().code()).isEqualTo("N");
    }

    @Test
    void quantitativeUsesParsedDecimal() {
        assertThat(FhirDocumentMapper.quantity("14.2", "g/dL"))
                .isEqualTo(FhirQuantity.of(new java.math.BigDecimal("14.2"), "g/dL"));
        assertThat(FhirDocumentMapper.quantity("128/82", "mmHg")).isNull();
        assertThat(FhirDocumentMapper.quantityString("128/82")).isEqualTo("128/82");
        assertThat(FhirDocumentMapper.quantityString("14.2")).isNull();
    }
}