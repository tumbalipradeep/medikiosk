package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentExtraction;
import in.devmedi.kiosk.module.document.entity.ClinicalDocumentPageText;
import in.devmedi.kiosk.module.document.extraction.ExtractionErrorCategory;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentExtractionRepository;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentRepository;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import in.devmedi.kiosk.module.physician.service.CompletedCaseReviewStore;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClinicalDocumentExtractionIntegrationTests {

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
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
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

    private byte[] encryptedPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Secret text");
                cs.endText();
            }
            document.protect(new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission()));
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

    @Test
    void physicianCanExtractTextFromAPdfInTheReviewedCase() throws Exception {
        String caseId = createCase("Left knee pain");
        String documentId = upload(caseId, "lab-report.pdf", "application/pdf",
                textPdf("Blood sugar fasting: 92 mg/dL",
                        "Complete blood count is within normal limits"));

        mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.pageCount").value(2))
                .andExpect(jsonPath("$.extractedText").value(containsString("Blood sugar fasting: 92 mg/dL")))
                .andExpect(jsonPath("$.extractedText").value(containsString("Complete blood count")))
                .andExpect(jsonPath("$.pages").isArray())
                .andExpect(jsonPath("$.pages[0].pageNumber").value(1))
                .andExpect(jsonPath("$.pages[0].text").value(containsString("Blood sugar")))
                .andExpect(jsonPath("$.pages[1].pageNumber").value(2))
                .andExpect(jsonPath("$.pages[1].text").value(containsString("Complete blood count")))
                .andExpect(jsonPath("$.errorCategory").value("NONE"));
    }

    @Test
    void extractionResultSurvivesPersistenceAndOriginalMetadataRemainsIntact() throws Exception {
        String caseId = createCase("Persistent cough");
        String documentId = upload(caseId, "cough-report.pdf", "application/pdf",
                textPdf("Lung fields are clear", "No abnormalities detected"));

        mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician())
                        .with(csrf()))
                .andExpect(status().isOk());

        ClinicalDocumentExtraction persisted =
                extractionRepository.findByDocumentIdWithPages(documentId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ExtractionStatus.EXTRACTED);
        assertThat(persisted.getPageCount()).isEqualTo(2);
        assertThat(persisted.getExtractedText())
                .contains("Lung fields are clear")
                .contains("No abnormalities detected");
        assertThat(persisted.getPages())
                .extracting(ClinicalDocumentPageText::getPageNumber)
                .containsExactly(1, 2);
        assertThat(persisted.getPages())
                .extracting(p -> p.getPageText().trim())
                .containsExactly("Lung fields are clear", "No abnormalities detected");

        ClinicalDocument original = documentRepository.findByDocumentId(documentId).orElseThrow();
        assertThat(original.getDocumentId()).isEqualTo(documentId);
        assertThat(original.getOriginalFilename()).isEqualTo("cough-report.pdf");
        assertThat(original.getContentType()).isEqualTo("application/pdf");
        assertThat(original.getFileSize()).isEqualTo(textPdf("Lung fields are clear", "No abnormalities detected").length);
        assertThat(original.getStoredFilename()).isNotBlank();
        assertThat(original.getUploadedAt()).isNotNull();
    }

    @Test
    void extractionIsServiceableAgainAfterAppRestartViaGet() throws Exception {
        String caseId = createCase("Left knee pain");
        String documentId = upload(caseId, "repeat.pdf", "application/pdf",
                textPdf("Same result after restart"));

        mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician())
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.extractedText").value(containsString("Same result after restart")));
    }

    @Test
    void documentFromAnUnrelatedCaseIsRejectedForExtraction() throws Exception {
        String caseA = createCase("First complaint");
        String caseB = createCase("Second complaint");
        String documentId = upload(caseA, "belongs-to-a.pdf", "application/pdf", textPdf("Belongs to case A"));

        mockMvc.perform(post("/physician/cases/" + caseB + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("given case")));

        mockMvc.perform(get("/physician/cases/" + caseB + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician()))
                .andExpect(status().isNotFound());
    }

    @Test
    void extractionOfUnknownDocumentReturnsNotFound() throws Exception {
        String caseId = createCase("Unknown doc");

        mockMvc.perform(post("/physician/cases/" + caseId + "/documents/doc-unknown/extraction")
                        .session(loginPhysician())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void patientCannotUsePhysicianExtractionEndpoint() throws Exception {
        String caseId = createCase("Patient tries to extract");
        String documentId = upload(caseId, "patient-privacy.pdf", "application/pdf", textPdf("Private"));

        mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPatient()))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankPdfWithoutTextIsReportedHonestlyAsNoText() throws Exception {
        String caseId = createCase("Blank scan");
        String documentId = upload(caseId, "blank-scan.pdf", "application/pdf", blankPdf());

        mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_TEXT"))
                .andExpect(jsonPath("$.extractedText").value(""))
                .andExpect(jsonPath("$.errorMessage").value(containsString("scanned images")));
    }

    @Test
    void imageWithoutOcrEngineIsReportedHonestlyAsUnsupported() throws Exception {
        String caseId = createCase("Scanned image");
        String documentId = upload(caseId, "prescription-scan.png", "image/png", imagePng());

        mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.errorCategory").value("NO_OCR_ENGINE"))
                // RD3: the honest message names the configured engine that is missing.
                .andExpect(jsonPath("$.errorMessage").value(containsString("is not available in this deployment")))
                .andExpect(jsonPath("$.extractedText").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void malformedPdfIsReportedHonestlyAsFailed() throws Exception {
        String caseId = createCase("Malformed upload");
        String documentId = upload(caseId, "broken.pdf", "application/pdf",
                "%PDF-1.4 oops this is defective %%EOF".getBytes());

        mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCategory").value("MALFORMED"))
                .andExpect(jsonPath("$.extractedText").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void encryptedPdfIsReportedHonestlyAsEncrypted() throws Exception {
        String caseId = createCase("Encrypted defence-report");
        String documentId = upload(caseId, "protected.pdf", "application/pdf", encryptedPdf());

        mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCategory").value("ENCRYPTED"));
    }

    @Test
    void physicianReviewShowsHonestExtractionStateBeforeAndAfterExtraction() throws Exception {
        String caseId = createCase("Review status flow");
        String documentId = upload(caseId, "status-flow.pdf", "application/pdf", textPdf("Status flow body"));

        MockHttpSession physician = loginPhysician();

        mockMvc.perform(get("/physician/cases/" + caseId).session(physician))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not extracted")))
                .andExpect(content().string(containsString("Extract text")))
                .andExpect(content().string(not(containsString("View extracted text"))));

        mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(physician)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/physician/cases/" + caseId).session(physician))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Text extracted")))
                .andExpect(content().string(containsString("View extracted text")))
                .andExpect(content().string(not(containsString("Extract text"))));
    }

    @Test
    void extractionResultsAreRemovedWhenTheirCaseIsDeleted() throws Exception {
        String caseId = createCase("Cleanup cascade");
        String documentId = upload(caseId, "cleanup.pdf", "application/pdf", textPdf("Cleanup me"));

        mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                        .session(loginPhysician())
                        .with(csrf()))
                .andExpect(status().isOk());

        casePersistence.deleteAll();

        assertThat(extractionRepository.findByDocumentIdWithPages(documentId)).isEmpty();
        assertThat(documentRepository.findByDocumentId(documentId)).isEmpty();
    }
}