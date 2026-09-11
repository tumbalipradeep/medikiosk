package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import in.devmedi.kiosk.module.document.findings.entity.ClinicalDocumentFindings;
import in.devmedi.kiosk.module.document.findings.repository.ClinicalDocumentFindingsRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentExtractionRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentRepository;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClinicalFindingsIntegrationTests {

    private static final String FIXTURE_TEXT = """
            ================= SUNRISE MULTISPECIALITY HOSPITAL, PUNE =================
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompletedCasePersistenceService casePersistence;

    @Autowired
    private CompletedCaseReviewStore reviewStore;

    @Autowired
    private ClinicalDocumentRepository documentRepository;

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
        documentRepository.deleteAllInBatch();
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

    private byte[] textPdf(String... pageTexts) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pageTexts) {
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
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] blankPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] imagePng() throws IOException {
        BufferedImage buffered = new BufferedImage(140, 60, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(buffered, "png", out);
            return out.toByteArray();
        }
    }

    private String extract(String caseId, String documentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void physicianSeesStructuredFindingsForAnExtractedDocument() throws Exception {
        String caseId = createCase("Routine follow-up");
        String documentId = upload(caseId, "fixture.pdf", "application/pdf", textPdf(FIXTURE_TEXT));
        extract(caseId, documentId);

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + documentId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.extractionStatus").value("EXTRACTED"))
                .andExpect(jsonPath("$.errorCategory").value("NONE"))
                .andExpect(jsonPath("$.findingsCreatedAt").isNotEmpty())
                .andExpect(jsonPath("$.patient.name").value("Rahul Sharma"))
                .andExpect(jsonPath("$.patient.dateOfBirth").value("15/08/1990"))
                .andExpect(jsonPath("$.patient.sex").value("Male"))
                .andExpect(jsonPath("$.patient.mrn").value("MRN-20419"))
                .andExpect(jsonPath("$.encounter.reportDate").value("05/06/2024"))
                .andExpect(jsonPath("$.encounter.encounterDate").value("03/06/2024"))
                .andExpect(jsonPath("$.encounter.facility").value("Sunrise Multispeciality Hospital, Pune"))
                .andExpect(jsonPath("$.encounter.clinician").value("Dr. Meera Iyer"))
                .andExpect(jsonPath("$.encounter.reportType").value("Lab Report"))
                .andExpect(jsonPath("$.vitals[0].type").value("BLOOD_PRESSURE"))
                .andExpect(jsonPath("$.vitals[0].value").value("128/82"))
                .andExpect(jsonPath("$.vitals[0].unit").value("mmHg"))
                .andExpect(jsonPath("$.vitals[7].type").value("BMI"))
                .andExpect(jsonPath("$.vitals[7].value").value("25.3"))
                .andExpect(jsonPath("$.vitals[7].unit").value("kg/m²"))
                .andExpect(jsonPath("$.labResults[0].testName").value("Hemoglobin"))
                .andExpect(jsonPath("$.labResults[0].value").value("13.5"))
                .andExpect(jsonPath("$.labResults[0].unit").value("g/dL"))
                .andExpect(jsonPath("$.labResults[0].referenceRange").value("13.0 - 17.0"))
                .andExpect(jsonPath("$.labResults[4].testName").value("HbA1C"))
                .andExpect(jsonPath("$.medications[0].name").value("Metformin"))
                .andExpect(jsonPath("$.medications[0].strength").value("500 mg"))
                .andExpect(jsonPath("$.medications[0].frequency").value("Twice a day"))
                .andExpect(jsonPath("$.medications[3].name").value("Cough Relief"))
                .andExpect(jsonPath("$.medications[3].dose").value("5 ml"));
    }

    @Test
    void findingsSurvivePersistenceInDocumentOrder() throws Exception {
        String caseId = createCase("Persistence check");
        String documentId = upload(caseId, "persist.pdf", "application/pdf", textPdf(FIXTURE_TEXT));
        extract(caseId, documentId);

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + documentId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vitals.length()").value(8))
                .andExpect(jsonPath("$.labResults.length()").value(11))
                .andExpect(jsonPath("$.medications.length()").value(4))
                .andExpect(jsonPath("$.vitals[0].type").value("BLOOD_PRESSURE"))
                .andExpect(jsonPath("$.vitals[7].type").value("BMI"))
                .andExpect(jsonPath("$.medications[3].name").value("Cough Relief"));

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + documentId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vitals[0].value").value("128/82"))
                .andExpect(jsonPath("$.vitals[7].value").value("25.3"))
                .andExpect(jsonPath("$.labResults[0].testName").value("Hemoglobin"))
                .andExpect(jsonPath("$.labResults[10].testName").value("TSH"));

        ClinicalDocumentExtraction extraction =
                extractionRepository.findByDocumentIdWithPages(documentId).orElseThrow();
        ClinicalDocumentFindings stored = findingsRepository.findByExtraction(extraction).orElseThrow();

        assertThat(stored.getPatientName()).isEqualTo("Rahul Sharma");
        assertThat(stored.getPatientMrn()).isEqualTo("MRN-20419");
        assertThat(stored.getReportDate()).isEqualTo("05/06/2024");
        assertThat(stored.getFacility()).isEqualTo("Sunrise Multispeciality Hospital, Pune");
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    @Test
    void findingsAreRecomputedOnDemandWhenMissing() throws Exception {
        String caseId = createCase("Regenerate find");
        String documentId = upload(caseId, "regenerate.pdf", "application/pdf", textPdf(FIXTURE_TEXT));
        extract(caseId, documentId);

        ClinicalDocumentExtraction extraction =
                extractionRepository.findByDocumentIdWithPages(documentId).orElseThrow();
        findingsRepository.findByExtraction(extraction).ifPresent(findingsRepository::delete);

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + documentId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.name").value("Rahul Sharma"))
                .andExpect(jsonPath("$.labResults.length()").value(11))
                .andExpect(jsonPath("$.findingsCreatedAt").isNotEmpty());

        assertThat(findingsRepository.findByExtraction(extraction)).isPresent();
    }

    @Test
    void documentsWithoutExtractableTextProduceEmptyFindings() throws Exception {
        String caseId = createCase("Blank and image");
        String blankId = upload(caseId, "blank.pdf", "application/pdf", blankPdf());
        extract(caseId, blankId);

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + blankId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractionStatus").value("NO_TEXT"))
                .andExpect(jsonPath("$.patient").value(nullValue()))
                .andExpect(jsonPath("$.encounter").value(nullValue()))
                .andExpect(jsonPath("$.vitals.length()").value(0))
                .andExpect(jsonPath("$.labResults.length()").value(0))
                .andExpect(jsonPath("$.medications.length()").value(0))
                .andExpect(jsonPath("$.findingsCreatedAt").value(nullValue()));

        String imageId = upload(caseId, "scan.png", "image/png", imagePng());
        extract(caseId, imageId);

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + imageId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractionStatus").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.patient").value(nullValue()))
                .andExpect(jsonPath("$.vitals.length()").value(0));
    }

    @Test
    void findingsForAnUnrelatedCaseOrUnknownDocumentReturnNotFound() throws Exception {
        String caseA = createCase("Belongs to A");
        String caseB = createCase("Belongs to B");
        String documentId = upload(caseA, "a-doc.pdf", "application/pdf", textPdf(FIXTURE_TEXT));
        extract(caseA, documentId);

        mockMvc.perform(get("/physician/cases/" + caseB + "/documents/" + documentId + "/findings")
                        .session(loginPhysician()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/physician/cases/" + caseA + "/documents/doc-unknown/findings")
                        .session(loginPhysician()))
                .andExpect(status().isNotFound());
    }

    @Test
    void patientCannotUseThePhysicianFindingsEndpoint() throws Exception {
        String caseId = createCase("Privacy");
        String documentId = upload(caseId, "private.pdf", "application/pdf", textPdf(FIXTURE_TEXT));
        extract(caseId, documentId);

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + documentId + "/findings")
                        .session(loginPatient()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewPageShowsFindingsButtonOnlyForExtractedDocuments() throws Exception {
        String caseId = createCase("Findings button flow");
        String documentId = upload(caseId, "findings-btn.pdf", "application/pdf", textPdf(FIXTURE_TEXT));

        MockHttpSession physician = loginPhysician();

        mockMvc.perform(get("/physician/review").session(physician))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("View structured findings"))));

        extract(caseId, documentId);

        mockMvc.perform(get("/physician/review").session(physician))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("View structured findings")))
                .andExpect(content().string(containsString("findings-view-btn")));
    }

    @Test
    void findingsAreRemovedWhenTheirCaseIsDeleted() throws Exception {
        String caseId = createCase("Cleanup");
        String documentId = upload(caseId, "cleanup.pdf", "application/pdf", textPdf(FIXTURE_TEXT));
        extract(caseId, documentId);

        ClinicalDocumentExtraction extraction =
                extractionRepository.findByDocumentIdWithPages(documentId).orElseThrow();
        assertThat(findingsRepository.findByExtraction(extraction)).isPresent();

        casePersistence.deleteAll();

        assertThat(findingsRepository.findByExtraction(extraction)).isEmpty();
        assertThat(extractionRepository.findByDocumentIdWithPages(documentId)).isEmpty();
        assertThat(documentRepository.findByDocumentId(documentId)).isEmpty();
    }
}