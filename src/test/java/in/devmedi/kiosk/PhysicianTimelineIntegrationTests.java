package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.document.findings.DocumentDetailResponse;
import in.devmedi.kiosk.module.document.findings.DocumentWorkspaceItem;
import in.devmedi.kiosk.module.document.findings.TimelineResponse;
import in.devmedi.kiosk.module.document.findings.model.TimelineEvent;
import in.devmedi.kiosk.module.document.findings.model.TimelineEventType;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import in.devmedi.kiosk.module.physician.service.CompletedCaseReviewStore;
import in.devmedi.kiosk.module.physician.service.PhysicianTimelineService;
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
class PhysicianTimelineIntegrationTests {

    private static final String DOC_A = """
            ================= SUNRISE MULTISPECIALITY HOSPITAL, PUNE =================
            Patient Name: Ananya Desai
            Date of Birth: 22-05-1985        Gender: Female
            MRN: MRN-99201
            Facility: Sunrise Multispeciality Hospital, Pune
            Report Type: Lab Report
            Report Date: 09/09/2024      Encounter Date: 08/09/2024
            Referring Physician: Dr. Meera Iyer

            VITALS
            Blood Pressure: 128/82 mmHg

            LABORATORY RESULTS
            Hemoglobin: 13.5 g/dL (13.0 - 17.0)
            Serum Creatinine: 1.2 mg/dL (0.6 - 1.2)

            MEDICATIONS
            Tab. Metformin 500 mg BD for 30 days
            Tab. Metformin 500 mg BD for 30 days
            """;

    private static final String DOC_B = """
            ================= SUNRISE MULTISPECIALITY HOSPITAL, PUNE =================
            Patient Name: Ananya Desai
            Date of Birth: 22-05-1985        Gender: Female
            MRN: MRN-99201
            Facility: Sunrise Multispeciality Hospital, Pune
            Report Type: Lab Report
            Report Date: 05/09/2024      Encounter Date: 04/09/2024
            Referring Physician: Dr. Meera Iyer

            LABORATORY RESULTS
            Serum Creatinine: 1.8 mg/dL (0.6 - 1.2)
            Total Cholesterol: 240 mg/dL (< 200)

            MEDICATIONS
            Tab. Amoxicillin 500 mg 1 tablet three times a day by mouth for 7 days
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
    private PhysicianTimelineService timelineService;

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

    private record LoadedCase(String caseId, String docAId, String docBId) {
    }

    private LoadedCase loadTwoDocuments() throws Exception {
        String caseId = createCase("Timeline");
        String docAId = upload(caseId, "timeline-lab-report-a.pdf", "application/pdf", textPdf(DOC_A));
        String docBId = upload(caseId, "timeline-lab-report-b.pdf", "application/pdf", textPdf(DOC_B));
        for (String documentId : List.of(docAId, docBId)) {
            mockMvc.perform(post("/physician/cases/" + caseId + "/documents/" + documentId + "/extraction")
                            .session(loginPhysician()).with(csrf()))
                    .andExpect(status().isOk());
        }
        return new LoadedCase(caseId, docAId, docBId);
    }

    @Test
    void workspaceReportsMetadataExtractionStateAndDerivedFindingsCounts() throws Exception {
        LoadedCase loaded = loadTwoDocuments();

        List<DocumentWorkspaceItem> items = timelineService.workspaceItems(loaded.caseId());
        assertThat(items).hasSize(2);

        DocumentWorkspaceItem itemA = items.stream()
                .filter(i -> i.originalFilename().equals("timeline-lab-report-a.pdf"))
                .findFirst().orElseThrow();
        assertThat(itemA.documentId()).isEqualTo(loaded.docAId());
        assertThat(itemA.fileSize()).isPositive();
        assertThat(itemA.extractionStatus().name()).isEqualTo("EXTRACTED");
        assertThat(itemA.hasFindings()).isTrue();
        assertThat(itemA.labCount()).isEqualTo(2);
        assertThat(itemA.abnormalLabCount()).isZero();
        assertThat(itemA.medicationCount()).isEqualTo(2);
        assertThat(itemA.duplicateMedCount()).isEqualTo(1);

        DocumentWorkspaceItem itemB = items.stream()
                .filter(i -> i.originalFilename().equals("timeline-lab-report-b.pdf"))
                .findFirst().orElseThrow();
        assertThat(itemB.hasFindings()).isTrue();
        assertThat(itemB.labCount()).isEqualTo(2);
        assertThat(itemB.abnormalLabCount()).isEqualTo(2);
        assertThat(itemB.medicationCount()).isEqualTo(1);
        assertThat(itemB.duplicateMedCount()).isZero();
    }

    @Test
    void workspaceAppearsOnTheReviewPage() throws Exception {
        LoadedCase loaded = loadTwoDocuments();

        mockMvc.perform(get("/physician/review").session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var workspace = result.getModelAndView().getModel().get("workspace");
                    assertThat(workspace).isNotNull();
                    assertThat(workspace).asList().hasSize(2);
                });
    }

    @Test
    void timelineListsDatedEventsChronologicallyThenUndatedInDocumentOrder() throws Exception {
        LoadedCase loaded = loadTwoDocuments();

        TimelineResponse response = timelineService.timeline(loaded.caseId());
        List<TimelineEvent> events = response.events();

        assertThat(events).extracting(TimelineEvent::eventType).contains(
                TimelineEventType.DOCUMENT_UPLOADED,
                TimelineEventType.REPORT_DATE,
                TimelineEventType.ENCOUNTER_DATE,
                TimelineEventType.VITALS,
                TimelineEventType.LAB_RESULT,
                TimelineEventType.MEDICATION);

        List<TimelineEvent> dated = events.stream()
                .filter(e -> e.eventDate() != null)
                .toList();
        assertThat(dated).extracting(TimelineEvent::eventDate)
                .containsExactly("04/09/2024", "05/09/2024", "08/09/2024", "09/09/2024");

        List<TimelineEvent> undated = events.stream()
                .filter(e -> e.eventDate() == null)
                .toList();
        assertThat(undated).extracting(TimelineEvent::sourceDocumentFilename)
                .containsSubsequence(
                        "timeline-lab-report-a.pdf",
                        "timeline-lab-report-a.pdf",
                        "timeline-lab-report-b.pdf",
                        "timeline-lab-report-b.pdf");

        assertThat(dated).extracting(TimelineEvent::sourceDocumentFilename)
                .containsExactly(
                        "timeline-lab-report-b.pdf",
                        "timeline-lab-report-b.pdf",
                        "timeline-lab-report-a.pdf",
                        "timeline-lab-report-a.pdf");
    }

    @Test
    void timelineExposesConflictingObservationsSeparatelyWithoutMerging() throws Exception {
        LoadedCase loaded = loadTwoDocuments();

        TimelineResponse response = timelineService.timeline(loaded.caseId());

        List<TimelineEvent> creatinine = response.events().stream()
                .filter(e -> e.eventType() == TimelineEventType.LAB_RESULT)
                .filter(e -> e.label().equals("Serum Creatinine"))
                .toList();

        assertThat(creatinine).hasSize(2);
        assertThat(creatinine.get(0).details()).contains("1.2");
        assertThat(creatinine.get(0).sourceDocumentFilename()).isEqualTo("timeline-lab-report-a.pdf");
        assertThat(creatinine.get(1).details()).contains("1.8");
        assertThat(creatinine.get(1).sourceDocumentFilename()).isEqualTo("timeline-lab-report-b.pdf");

        assertThat(creatinine.get(0).sourceSnippet()).contains("Serum Creatinine: 1.2");
        assertThat(creatinine.get(1).sourceSnippet()).contains("Serum Creatinine: 1.8");
    }

    @Test
    void timelineKeepsDuplicateMedicationsAndTheirSnippets() throws Exception {
        LoadedCase loaded = loadTwoDocuments();

        TimelineResponse response = timelineService.timeline(loaded.caseId());
        List<TimelineEvent> metformin = response.events().stream()
                .filter(e -> e.eventType() == TimelineEventType.MEDICATION)
                .filter(e -> e.label().equals("Metformin"))
                .toList();

        assertThat(metformin).hasSize(2);
        assertThat(metformin).allMatch(e -> e.sourceDocumentFilename().equals("timeline-lab-report-a.pdf"));
        assertThat(metformin.get(0).details()).contains("500 mg").contains("Twice a day").contains("×2");
        assertThat(metformin.get(0).sourceSnippet()).contains("Tab. Metformin");
    }

    @Test
    void timelineIsDeterministicAcrossRepeatedReads() throws Exception {
        LoadedCase loaded = loadTwoDocuments();

        List<String> first = timelineService.timeline(loaded.caseId()).events()
                .stream().map(e -> e.eventType() + "|" + e.eventDate() + "|" + e.label()).toList();
        List<String> second = timelineService.timeline(loaded.caseId()).events()
                .stream().map(e -> e.eventType() + "|" + e.eventDate() + "|" + e.label()).toList();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void timelineEndpointIsPhysicianOnlyAndEmptyCollectionsNeverNull() throws Exception {
        LoadedCase loaded = loadTwoDocuments();

        mockMvc.perform(get("/physician/cases/" + loaded.caseId() + "/timeline")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events.length()").value(14));

        mockMvc.perform(get("/physician/cases/" + loaded.caseId() + "/timeline")
                        .session(loginPatient()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/physician/cases/does-not-exist/timeline")
                        .session(loginPhysician()))
                .andExpect(status().isNotFound());

        String otherCase = createCase("Other case");
        mockMvc.perform(get("/physician/cases/" + otherCase + "/timeline")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(0));
    }

    @Test
    void detailEndpointReturnsMetadataExtractionAndFindingsWhereAvailable() throws Exception {
        LoadedCase loaded = loadTwoDocuments();

        mockMvc.perform(get("/physician/cases/" + loaded.caseId()
                                + "/documents/" + loaded.docAId() + "/detail")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("timeline-lab-report-a.pdf"))
                .andExpect(jsonPath("$.extractionStatus").value("EXTRACTED"))
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.patient.name").value("Ananya Desai"))
                .andExpect(jsonPath("$.encounter.reportDate").value("09/09/2024"))
                .andExpect(jsonPath("$.labResults.length()").value(2))
                .andExpect(jsonPath("$.medications.length()").value(2))
                .andExpect(jsonPath("$.interactionAnalysisStatus").value("NOT_AVAILABLE"));

        DocumentDetailResponse detail = timelineService.documentDetail(loaded.caseId(), loaded.docAId());
        assertThat(detail.pages()).extracting(p -> p.pageNumber()).containsExactly(1);
        assertThat(detail.pages().get(0).text()).contains("Blood Pressure: 128/82");
        assertThat(detail.vitals()).hasSize(1);
        assertThat(detail.vitals().get(0).value()).isEqualTo("128/82");

        mockMvc.perform(get("/physician/cases/" + loaded.caseId()
                                + "/documents/" + loaded.docAId() + "/detail")
                        .session(loginPatient()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/physician/cases/" + loaded.caseId()
                                + "/documents/" + loaded.docBId() + "/detail")
                        .session(loginPhysician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounter.reportDate").value("05/09/2024"));

        String otherCase = createCase("Detail case");
        mockMvc.perform(get("/physician/cases/" + otherCase
                                + "/documents/" + loaded.docAId() + "/detail")
                        .session(loginPhysician()))
                .andExpect(status().isNotFound());
    }

    @Test
    void detailForUnExtractedDocumentShowsHonestPendingState() throws Exception {
        String caseId = createCase("Pending detail");
        String documentId = upload(caseId, "pending-scan.pdf", "application/pdf",
                textPdf("Patient Name: Samir Kulkarni\nReport Date: 01/01/2024"));

        DocumentDetailResponse detail = timelineService.documentDetail(caseId, documentId);
        assertThat(detail.extractionStatus().name()).isEqualTo("PENDING");
        assertThat(detail.pages()).isEmpty();
        assertThat(detail.patient()).isNull();
        assertThat(detail.encounter()).isNull();
        assertThat(detail.labResults()).isEmpty();

        TimelineResponse response = timelineService.timeline(caseId);
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).eventType()).isEqualTo(TimelineEventType.DOCUMENT_UPLOADED);
        assertThat(response.events().get(0).sourceDocumentFilename()).isEqualTo("pending-scan.pdf");
        assertThat(response.events().get(0).eventDate()).isNull();
    }
}