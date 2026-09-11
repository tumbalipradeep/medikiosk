package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindings;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindingsLab;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindingsMedication;
import in.devmedi.kiosk.module.document.findings.repository.ClinicalDocumentFindingsRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentExtractionRepository;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import in.devmedi.kiosk.module.physician.service.CompletedCaseReviewStore;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClinicalFindingsAnalysisIntegrationTests {

    private static final String FIXTURE_TEXT = """
            ================= SUNRISE MULTISPECIALITY HOSPITAL, PUNE =================
            Patient Name: Ananya Desai
            Date of Birth: 22-05-1985        Gender: Female
            MRN: MRN-99201
            Facility: Sunrise Multispeciality Hospital, Pune
            Report Type: Lab Report
            Report Date: 09/09/2024      Encounter Date: 08/09/2024
            Referring Physician: Dr. Meera Iyer

            LABORATORY RESULTS
            Hemoglobin: 13.5 g/dL (13.0 - 17.0)
            Hematocrit: 30 % (40 - 52)
            Total Cholesterol: 220 mg/dL (< 200)
            LDL Cholesterol: 100 mg/dL (60 - 100)
            Fasting Blood Sugar: 98 mg/dL (<= 100)
            Serum Creatinine: 1.4 mg/dL (0.6 - 1.2)
            Platelet Count: 2.5 lakh/cu.mm (2.0 - 4.5 lakh/cu.mm)
            TSH: 3.0 µIU/mL

            MEDICATIONS
            Tab. Metformin 500 mg BD for 30 days
            Tab. Metformin 500 mg BD for 30 days
            Tab. Amoxicillin 500 mg 1 tablet three times a day by mouth for 7 days
            Tab. Atorvastatin 10 mg HS
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompletedCasePersistenceService casePersistence;

    @Autowired
    private CompletedCaseReviewStore reviewStore;

    @Autowired
    private ClinicalDocumentExtractionRepository extractionRepository;

    @Autowired
    private ClinicalDocumentFindingsRepository findingsRepository;

    @Autowired
    @Value("${medikiosk.documents.upload-dir}")
    private String uploadDir;

    private Long patientUserId;

    @BeforeEach
    void setUp() {
        reviewStore.clear();
        casePersistence.deleteAll();
        patientUserId = userRepository.findByUsername("patient").orElseThrow().getId();
    }

    @AfterEach
    void cleanFiles() throws Exception {
        try {
            Files.walk(Path.of(uploadDir)).filter(Files::isRegularFile).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession loginPatient() throws Exception {
        return login("patient", "patient123");
    }

    private MockHttpSession loginPhysician() throws Exception {
        return login("physician", "physician123");
    }

    private String createCase(String answer) {
        ClinicalConversationResult result = new ClinicalConversationResult();
        result.record(new ClinicalAnswer("chief_complaint_symptom", "CHIEF_COMPLAINT",
                "SYMPTOM_PROBLEM", "Describe your problem", answer));
        CompletedCase completed = CompletedCase.withNewId(result, patientUserId);
        casePersistence.save(completed, patientUserId);
        return completed.id();
    }

    private String upload(String caseId, String filename, String contentType, byte[] bytes) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(new MockMultipartFile("file", filename, contentType, bytes))
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.documentId");
    }

    private byte[] textPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                float y = 760;
                for (String line : text.split("\n", -1)) {
                    cs.newLineAtOffset(50, y);
                    cs.showText(line);
                    cs.newLineAtOffset(-50, -y);
                    y -= 14;
                    if (y < 60) {
                        break;
                    }
                }
                cs.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private String uploadAndExtract(String answer, String filename, String text) throws Exception {
        String caseId = createCase(answer);
        String documentId = upload(caseId, filename, "application/pdf", textPdf(text));
        mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician())
                        .with(csrf()))
                .andExpect(status().isOk());
        return caseId + "::" + documentId;
    }

    @Test
    void labAbnormalityStatusesAreComputedFromTheDocumentsOwnReferenceRange() throws Exception {
        String ref = uploadAndExtract("Analysis", "analysis.pdf", FIXTURE_TEXT);
        String[] ids = ref.split("::");
        String caseId = ids[0];
        String documentId = ids[1];

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + documentId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interactionAnalysisStatus").value("NOT_AVAILABLE"))
                .andExpect(jsonPath("$.labResults[0].testName").value("Hemoglobin"))
                .andExpect(jsonPath("$.labResults[0].abnormalityStatus").value("NORMAL"))
                .andExpect(jsonPath("$.labResults[0].referenceRange").value("13.0 - 17.0"))
                .andExpect(jsonPath("$.labResults[0].rawValue").value("13.5"))
                .andExpect(jsonPath("$.labResults[1].testName").value("PCV / Hematocrit"))
                .andExpect(jsonPath("$.labResults[1].abnormalityStatus").value("LOW"))
                .andExpect(jsonPath("$.labResults[2].testName").value("Total Cholesterol"))
                .andExpect(jsonPath("$.labResults[2].abnormalityStatus").value("HIGH"))
                .andExpect(jsonPath("$.labResults[2].referenceRange").value("< 200"))
                .andExpect(jsonPath("$.labResults[3].testName").value("LDL Cholesterol"))
                .andExpect(jsonPath("$.labResults[3].abnormalityStatus").value("NORMAL"))
                .andExpect(jsonPath("$.labResults[4].testName").value("Fasting Blood Sugar"))
                .andExpect(jsonPath("$.labResults[4].abnormalityStatus").value("NORMAL"))
                .andExpect(jsonPath("$.labResults[5].testName").value("Serum Creatinine"))
                .andExpect(jsonPath("$.labResults[5].abnormalityStatus").value("HIGH"))
                .andExpect(jsonPath("$.labResults[6].testName").value("Platelet Count"))
                .andExpect(jsonPath("$.labResults[6].abnormalityStatus").value("NORMAL"))
                .andExpect(jsonPath("$.labResults[6].referenceRange").value("2.0 - 4.5 lakh/cu.mm"))
                .andExpect(jsonPath("$.labResults[7].testName").value("TSH"))
                .andExpect(jsonPath("$.labResults[7].abnormalityStatus").value("UNKNOWN"));
    }

    @Test
    void medicationCompletenessAndDuplicatesAreReportedWithoutInventingValues() throws Exception {
        String ref = uploadAndExtract("Analysis meds", "analysis-meds.pdf", FIXTURE_TEXT);
        String[] ids = ref.split("::");
        String caseId = ids[0];
        String documentId = ids[1];

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + documentId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labResults.length()").value(8))
                .andExpect(jsonPath("$.medications.length()").value(4))
                .andExpect(jsonPath("$.medications[0].name").value("Metformin"))
                .andExpect(jsonPath("$.medications[0].completeness").value("INCOMPLETE"))
                .andExpect(jsonPath("$.medications[0].duplicateCount").value(2))
                .andExpect(jsonPath("$.medications[1].name").value("Metformin"))
                .andExpect(jsonPath("$.medications[1].duplicateCount").value(2))
                .andExpect(jsonPath("$.medications[2].name").value("Amoxicillin"))
                .andExpect(jsonPath("$.medications[2].completeness").value("COMPLETE"))
                .andExpect(jsonPath("$.medications[2].duplicateCount").value(1))
                .andExpect(jsonPath("$.medications[2].dose").value("1 tablet"))
                .andExpect(jsonPath("$.medications[2].route").value("Oral"))
                .andExpect(jsonPath("$.medications[3].name").value("Atorvastatin"))
                .andExpect(jsonPath("$.medications[3].completeness").value("INCOMPLETE"))
                .andExpect(jsonPath("$.medications[3].duplicateCount").value(1));
    }

    @Test
    @Transactional
    void analysisOutcomesSurvivePersistenceAndRepeatedReads() throws Exception {
        String ref = uploadAndExtract("Analysis persistence", "analysis-persist.pdf", FIXTURE_TEXT);
        String[] ids = ref.split("::");
        String caseId = ids[0];
        String documentId = ids[1];

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + documentId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labResults[5].abnormalityStatus").value("HIGH"))
                .andExpect(jsonPath("$.medications[0].duplicateCount").value(2));

        ClinicalDocumentExtraction extraction =
                extractionRepository.findByDocumentIdWithPages(documentId).orElseThrow();
        ClinicalDocumentFindings stored = findingsRepository.findByExtraction(extraction).orElseThrow();

        assertThat(stored.getInteractionAnalysisStatus()).isEqualTo("NOT_AVAILABLE");
        List<ClinicalDocumentFindingsLab> labs = stored.getLabResults();
        assertThat(labs).hasSize(8);
        assertThat(labs.get(5).getTestName()).isEqualTo("Serum Creatinine");
        assertThat(labs.get(5).getAbnormalityStatus()).isEqualTo("HIGH");
        assertThat(labs.get(6).getAbnormalityStatus()).isEqualTo("NORMAL");
        assertThat(labs.get(6).getRawValue()).isEqualTo("2.5");
        assertThat(labs.get(7).getAbnormalityStatus()).isEqualTo("UNKNOWN");

        List<ClinicalDocumentFindingsMedication> medications = stored.getMedications();
        assertThat(medications).hasSize(4);
        assertThat(medications.get(0).getCompletenessStatus()).isEqualTo("INCOMPLETE");
        assertThat(medications.get(0).getMissingFields()).contains("DOSE").contains("ROUTE");
        assertThat(medications.get(0).getDuplicateCount()).isEqualTo(2);
        assertThat(medications.get(2).getCompletenessStatus()).isEqualTo("COMPLETE");
        assertThat(medications.get(2).getDuplicateCount()).isEqualTo(1);

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + documentId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labResults[5].abnormalityStatus").value("HIGH"))
                .andExpect(jsonPath("$.medications[2].completeness").value("COMPLETE"));
    }

    @Test
    void analysisIsIdempotentlyRecomputedWhenTheFindingsRowIsMissing() throws Exception {
        String ref = uploadAndExtract("Analysis regen", "analysis-regenerate.pdf", FIXTURE_TEXT);
        String[] ids = ref.split("::");
        String caseId = ids[0];
        String documentId = ids[1];

        ClinicalDocumentExtraction extraction =
                extractionRepository.findByDocumentIdWithPages(documentId).orElseThrow();
        findingsRepository.findByExtraction(extraction).ifPresent(findingsRepository::delete);

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + documentId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labResults[7].abnormalityStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.medications[0].duplicateCount").value(2))
                .andExpect(jsonPath("$.interactionAnalysisStatus").value("NOT_AVAILABLE"));

        ClinicalDocumentFindings regenerated = findingsRepository.findByExtraction(extraction).orElseThrow();
        assertThat(regenerated.getInteractionAnalysisStatus()).isEqualTo("NOT_AVAILABLE");
    }

    @Test
    void analysisIsScopedByCaseAndRoleLikeTheBaseFindingsEndpoint() throws Exception {
        String caseA = createCase("Analysis case A");
        String caseB = createCase("Analysis case B");
        String documentId = upload(caseA, "analysis-a.pdf", "application/pdf", textPdf(FIXTURE_TEXT));
        mockMvc.perform(post("/physician/cases/" + caseA + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/physician/cases/" + caseB + "/documents/" + documentId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/physician/cases/" + caseA + "/documents/" + documentId + "/findings")
                        .session(loginPatient()))
                .andExpect(status().isForbidden());
    }
}