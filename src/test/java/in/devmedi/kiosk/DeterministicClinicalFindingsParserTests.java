package in.devmedi.kiosk;

import in.devmedi.kiosk.module.document.findings.model.EncounterMetadata;
import in.devmedi.kiosk.module.document.findings.model.LabResult;
import in.devmedi.kiosk.module.document.findings.model.Medication;
import in.devmedi.kiosk.module.document.findings.model.PatientIdentifiers;
import in.devmedi.kiosk.module.document.findings.model.StructuredFindings;
import in.devmedi.kiosk.module.document.findings.model.VitalSign;
import in.devmedi.kiosk.module.document.findings.model.VitalType;
import in.devmedi.kiosk.module.document.findings.parser.DeterministicClinicalFindingsParser;
import in.devmedi.kiosk.module.document.findings.parser.LabTestRegistry;
import in.devmedi.kiosk.module.document.findings.parser.MedicationNameRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicClinicalFindingsParserTests {

    private final DeterministicClinicalFindingsParser parser =
            new DeterministicClinicalFindingsParser(new LabTestRegistry(), new MedicationNameRegistry());

    private static final String FIXTURE = """
            ==========================================
                    SUNRISE MULTISPECIALITY HOSPITAL, PUNE
            ==========================================
            Patient Name: Rahul Sharma
            Date of Birth: 15-08-1990        Gender: Male
            MRN: MRN-20419
            Facility: Sunrise Multispeciality Hospital, Pune
            Report Type: Lab Report
            Report Date: 05/06/2024      Encounter Date: 03/06/2024
            Referring Physician: Dr. Meera Iyer

            VITALS
            Blood Pressure: 128/82 mmHg
            Pulse: 76 bpm
            Respiratory Rate: 16 breaths/min
            Temperature: 98.6 deg F
            SpO2: 98 %
            Height: 171 cm   Weight: 74 kg   BMI: 25.3 kg/m2

            LABORATORY RESULTS
            Hemoglobin: 13.5 g/dL (13.0 - 17.0)
            Total WBC Count: 7200 /µL (4000 - 11000)
            Platelet Count: 2.5 lakh/cu.mm (150000 - 450000)
            Fasting Blood Sugar: 118 mg/dL (70 - 100)
            HbA1C: 5.9 % (4.0 - 5.6)
            Total Cholesterol: 172 mg/dL (140 - 200)
            Triglycerides: 150 mg/dL (40 - 160)
            HDL Cholesterol: 45 mg/dL (35 - 60)
            LDL Cholesterol: 98 mg/dL (60 - 130)
            Serum Creatinine: 0.9 mg/dL (0.6 - 1.2)
            TSH: 3.2 µIU/mL (0.4 - 4.0)

            MEDICATIONS
            Tab. Metformin 500 mg BD for 30 days
            Tab. Amoxicillin 500 mg TDS for 7 days
            Tab. Atorvastatin 10 mg HS
            Syp. Cough Relief 5 ml BD
            """;

    @Test
    void parsesPatientIdentifiersFromFixture() {
        PatientIdentifiers patient = parser.parse(FIXTURE).patient();

        assertThat(patient).isNotNull();
        assertThat(patient.name()).isEqualTo("Rahul Sharma");
        assertThat(patient.dateOfBirth()).isEqualTo("15/08/1990");
        assertThat(patient.sex()).isEqualTo("Male");
        assertThat(patient.mrn()).isEqualTo("MRN-20419");
    }

    @Test
    void parsesEncounterMetadataFromFixture() {
        EncounterMetadata encounter = parser.parse(FIXTURE).encounter();

        assertThat(encounter).isNotNull();
        assertThat(encounter.reportDate()).isEqualTo("05/06/2024");
        assertThat(encounter.encounterDate()).isEqualTo("03/06/2024");
        assertThat(encounter.facility()).isEqualTo("Sunrise Multispeciality Hospital, Pune");
        assertThat(encounter.clinician()).isEqualTo("Dr. Meera Iyer");
        assertThat(encounter.reportType()).isEqualTo("Lab Report");
    }

    @Test
    void parsesAllVitalsInDocumentOrder() {
        List<VitalSign> vitals = parser.parse(FIXTURE).vitals();

        assertThat(vitals).extracting(VitalSign::type).containsExactly(
                VitalType.BLOOD_PRESSURE,
                VitalType.HEART_RATE,
                VitalType.RESPIRATORY_RATE,
                VitalType.TEMPERATURE,
                VitalType.OXYGEN_SATURATION,
                VitalType.HEIGHT,
                VitalType.WEIGHT,
                VitalType.BMI);

        assertThat(vitals).extracting(VitalSign::value).containsExactly(
                "128/82", "76", "16", "98.6", "98", "171", "74", "25.3");
        assertThat(vitals).extracting(VitalSign::unit).containsExactly(
                "mmHg", "bpm", "breaths/min", "°F", "%", "cm", "kg", "kg/m²");
        assertThat(vitals).extracting(VitalSign::occurrenceIndex)
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void parsesLabResultsWithValuesUnitsAndReferenceRanges() {
        List<LabResult> labs = parser.parse(FIXTURE).labResults();

        assertThat(labs).hasSize(11);
        assertThat(labs).extracting(LabResult::testName).containsExactly(
                "Hemoglobin", "Total WBC Count", "Platelet Count", "Fasting Blood Sugar", "HbA1C",
                "Total Cholesterol", "Triglycerides", "HDL Cholesterol", "LDL Cholesterol",
                "Serum Creatinine", "TSH");

        assertThat(labs).extracting(LabResult::value).containsExactly(
                "13.5", "7200", "2.5", "118", "5.9", "172", "150", "45", "98", "0.9", "3.2");
        assertThat(labs).extracting(LabResult::unit).containsExactly(
                "g/dL", "/µL", "lakh/cu.mm", "mg/dL", "%",
                "mg/dL", "mg/dL", "mg/dL", "mg/dL", "mg/dL", "µIU/mL");
        assertThat(labs).extracting(LabResult::referenceRange).containsExactly(
                "13.0 - 17.0", "4000 - 11000", "150000 - 450000", "70 - 100", "4.0 - 5.6",
                "140 - 200", "40 - 160", "35 - 60", "60 - 130", "0.6 - 1.2", "0.4 - 4.0");
        assertThat(labs).extracting(LabResult::occurrenceIndex)
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void parsesMedicationsWithFieldsOnlyAsPresent() {
        List<Medication> medications = parser.parse(FIXTURE).medications();

        assertThat(medications).hasSize(4);
        assertThat(medications).extracting(Medication::name)
                .containsExactly("Metformin", "Amoxicillin", "Atorvastatin", "Cough Relief");

        Medication metformin = medications.get(0);
        assertThat(metformin.strength()).isEqualTo("500 mg");
        assertThat(metformin.dose()).isNull();
        assertThat(metformin.frequency()).isEqualTo("Twice a day");
        assertThat(metformin.duration()).isEqualTo("30 days");

        Medication amoxicillin = medications.get(1);
        assertThat(amoxicillin.strength()).isEqualTo("500 mg");
        assertThat(amoxicillin.frequency()).isEqualTo("Three times a day");
        assertThat(amoxicillin.duration()).isEqualTo("7 days");

        Medication atorvastatin = medications.get(2);
        assertThat(atorvastatin.strength()).isEqualTo("10 mg");
        assertThat(atorvastatin.frequency()).isEqualTo("At bedtime");

        Medication coughRelief = medications.get(3);
        assertThat(coughRelief.strength()).isNull();
        assertThat(coughRelief.dose()).isEqualTo("5 ml");
        assertThat(coughRelief.frequency()).isEqualTo("Twice a day");
    }

    @Test
    void rawSourceSnippetIsTheOriginatingLine() {
        List<VitalSign> vitals = parser.parse(FIXTURE).vitals();
        assertThat(vitals)
                .filteredOn(v -> v.type() == VitalType.BLOOD_PRESSURE)
                .singleElement()
                .satisfies(v -> assertThat(v.sourceSnippet()).isEqualTo("Blood Pressure: 128/82 mmHg"));

        List<LabResult> labs = parser.parse(FIXTURE).labResults();
        assertThat(labs)
                .filteredOn(l -> l.testName().equals("Hemoglobin"))
                .singleElement()
                .satisfies(l -> assertThat(l.sourceSnippet()).contains("13.5 g/dL"));
    }

    @Test
    void duplicateResultsArePreservedAsOrderedOccurrences() {
        String text = "Hemoglobin: 13.5 g/dL\nHemoglobin: 12.1 g/dL\n";

        List<LabResult> labs = parser.parse(text).labResults();

        assertThat(labs).hasSize(2);
        assertThat(labs.get(0).value()).isEqualTo("13.5");
        assertThat(labs.get(1).value()).isEqualTo("12.1");
        assertThat(labs).extracting(LabResult::occurrenceIndex).containsExactly(0, 1);
    }

    @Test
    void parsingIsDeterministic() {
        StructuredFindings first = parser.parse(FIXTURE);
        StructuredFindings second = parser.parse(FIXTURE);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void unlabelledFreetextProducesEmptyFindingsWithoutGuessing() {
        String text = """
                Chief complaint: Fever since 3 days.
                Patient reports mild headache. No past history of note.
                """;

        StructuredFindings findings = parser.parse(text);

        assertThat(findings.patient()).isNull();
        assertThat(findings.encounter()).isNull();
        assertThat(findings.vitals()).isEmpty();
        assertThat(findings.labResults()).isEmpty();
        assertThat(findings.medications()).isEmpty();
    }

    @Test
    void labLinesWithoutAParsableValueAreLeftUnparsed() {
        String text = """
                Hemoglobin was within normal limits.
                HbA1C - controlled.
                Blood sugar is fine.
                """;

        assertThat(parser.parse(text).labResults()).isEmpty();
    }

    @Test
    void proseThatMerelyMentionsVitalsIsNotDecoded() {
        String text = """
                Pulse and blood pressure were monitored.
                Weight was checked during the visit.
                """;

        assertThat(parser.parse(text).vitals()).isEmpty();
    }

    @Test
    void prescriptionWithoutAnyFieldIsRejected() {
        String text = "Take medicines regularly\nPatient uses a tablet daily\n";

        assertThat(parser.parse(text).medications()).isEmpty();
    }

    @Test
    void acceptsUnitAfterReferenceRangeWithFlags() {
        String text = "Hemoglobin: 13.5 (13.0 - 17.0) g/dL (Normal)\n";

        List<LabResult> labs = parser.parse(text).labResults();

        assertThat(labs).hasSize(1);
        assertThat(labs.get(0).value()).isEqualTo("13.5");
        assertThat(labs.get(0).unit()).isEqualTo("g/dL");
        assertThat(labs.get(0).referenceRange()).isEqualTo("13.0 - 17.0");
    }

    @Test
    void mostSpecificLabAliasAlwaysWins() {
        String text = """
                Free T4: 1.3 ng/dL (0.8 - 1.8)
                T4: 8.5 µg/dL (5.1 - 14.1)
                """;

        List<LabResult> labs = parser.parse(text).labResults();

        assertThat(labs).extracting(LabResult::testName).containsExactly("Free T4", "Total T4");
        assertThat(labs).extracting(LabResult::value).containsExactly("1.3", "8.5");
    }
}