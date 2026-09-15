package in.devmedi.kiosk;

import in.devmedi.kiosk.module.auth.entity.Role;
import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalAnswer;
import in.devmedi.kiosk.module.clinical.dialogue.ClinicalConversationResult;
import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentRepository;
import in.devmedi.kiosk.module.physician.service.CompletedCase;
import in.devmedi.kiosk.module.physician.service.CompletedCasePersistenceService;
import in.devmedi.kiosk.module.physician.service.CompletedCaseReviewStore;
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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClinicalDocumentIntegrationTests {

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

    @Value("${medikiosk.documents.upload-dir}")
    private String uploadDir;

    private String caseId;
    private Long patientUserId;

    @BeforeEach
    void setUp() throws Exception {
        reviewStore.clear();
        documentRepository.deleteAllInBatch();
        casePersistence.deleteAll();

        patientUserId = userRepository.findByUsername("patient").orElseThrow().getId();
        ClinicalConversationResult result = new ClinicalConversationResult();
        result.record(new ClinicalAnswer("chief_complaint_symptom", "CHIEF_COMPLAINT",
                "SYMPTOM_PROBLEM", "Describe your problem", "Headache"));
        CompletedCase completed = CompletedCase.withNewId(result, patientUserId);
        casePersistence.save(completed, patientUserId);
        caseId = completed.id();
        Files.createDirectories(Path.of(uploadDir));
    }

    @AfterEach
    void cleanFiles() {
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

    @Test
    void patientCanUploadAPdfAndMetadataIsReturned() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lab-report.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D});

        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(org.hamcrest.Matchers.startsWith("doc-")))
                .andExpect(jsonPath("$.originalFilename").value("lab-report.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.fileSize").value(5));
    }

    @Test
    void uploadedFileIsStoredOnDiskUnderAFreshUuidName() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "scan.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10});

        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isOk());

        ClinicalDocument doc = documentRepository.findByCompletedCase_CaseIdOrderByUploadedAtAsc(caseId).getFirst();
        assertThat(doc.getStoredFilename()).isNotEqualTo("scan.jpg").endsWith(".jpg");
        Path stored = Path.of(uploadDir, doc.getStoredFilename());
        assertThat(Files.exists(stored)).isTrue();
        assertThat(Files.readAllBytes(stored)).containsExactly((byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10);
    }

    @Test
    void patientCanListOnlyTheirOwnDocumentsForTheCase() throws Exception {
        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(new MockMultipartFile("file", "one.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}))
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/patient/cases/" + caseId + "/documents")
                        .session(loginPatient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].originalFilename").value("one.pdf"));
    }

    @Test
    void unsupportedFileExtensionIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Unsupported file type")));
    }

    @Test
    void uploadedFileWithMismatchedContentTypeIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "text/plain", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Unsupported content type")));
    }

    @Test
    void uploadedFileWhoseNameExtensionDoesNotMatchDeclaredContentTypeIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.png", "image/jpeg", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("extension does not match")));
    }

    @Test
    void mimeSpoofedFileWhosePayloadIsNotTheDeclaredFormatIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.png", "image/png", "<html><script>alert(1)</script></html>".getBytes());

        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("spoofed")));
    }

    @Test
    void pdfWhosePayloadDoesNotStartWithPdfMagicIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake-report.pdf", "application/pdf", "NOTAPDF-JUSTTEXT".getBytes());

        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("spoofed")));
    }

    @Test
    void genuineJpegMagicBytesAreAccepted() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "real-photo.jpeg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x00, 0x10});

        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void genuinePngMagicBytesAreAccepted() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "real-scan.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00});

        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File is required"));
    }

    @Test
    void uploadForUnknownCaseIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[]{1});

        mockMvc.perform(multipart("/patient/cases/case-missing/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patientCannotUploadToCaseOwnedByAnotherUser() throws Exception {
        Long otherId = userRepository.findByUsername("physician").orElseThrow().getId();
        ClinicalConversationResult otherResult = new ClinicalConversationResult();
        otherResult.record(new ClinicalAnswer("chief_complaint_symptom", "CHIEF_COMPLAINT",
                "SYMPTOM_PROBLEM", "Describe your problem", "Pain"));
        CompletedCase otherCase = CompletedCase.withNewId(otherResult, otherId);
        casePersistence.save(otherCase, otherId);

        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D});

        mockMvc.perform(multipart("/patient/cases/" + otherCase.id() + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("your own case")));
    }

    @Test
    void patientCanDeleteTheirOwnDocumentAndTheFileIsRemoved() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "delete-me.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D});
        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isOk());

        ClinicalDocument doc = documentRepository.findByCompletedCase_CaseIdOrderByUploadedAtAsc(caseId).getFirst();

        mockMvc.perform(delete("/patient/cases/" + caseId + "/documents/" + doc.getDocumentId())
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        assertThat(documentRepository.findByCompletedCase_CaseIdOrderByUploadedAtAsc(caseId)).isEmpty();
        assertThat(Files.exists(Path.of(uploadDir, doc.getStoredFilename()))).isFalse();
    }

    @Test
    void physicianCannotDeleteAPatientsDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "mine.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D});
        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isOk());

        ClinicalDocument doc = documentRepository.findByCompletedCase_CaseIdOrderByUploadedAtAsc(caseId).getFirst();

        mockMvc.perform(delete("/patient/cases/" + caseId + "/documents/" + doc.getDocumentId())
                        .session(login("physician", "physician123"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingANonExistentDocumentReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/patient/cases/" + caseId + "/documents/doc-unknown")
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherPatientCannotUploadIntoMyCase() throws Exception {
        User other = userRepository.save(new User("patient_doc_" + System.nanoTime(),
                "$2a$10$xVtr2X.QvKLsn8HBQP7Ac.qixWbgqX8JOYO1ranOqZkR9t3Ynj5aW",
                "Other Patient", Role.PATIENT));
        MockHttpSession otherSession = login(other.getUsername(), "patient123");

        MockMultipartFile file = new MockMultipartFile(
                "file", "prying.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D});
        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(otherSession)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("You can only upload documents to your own case"));

        assertThat(documentRepository.findByCompletedCase_CaseIdOrderByUploadedAtAsc(caseId)).isEmpty();
    }

    @Test
    void anotherPatientListingMyCaseDocumentsSeesNothing() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "mine.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D});
        mockMvc.perform(multipart("/patient/cases/" + caseId + "/documents")
                        .file(file)
                        .session(loginPatient())
                        .with(csrf()))
                .andExpect(status().isOk());

        User other = userRepository.save(new User("patient_doc_" + System.nanoTime(),
                "$2a$10$xVtr2X.QvKLsn8HBQP7Ac.qixWbgqX8JOYO1ranOqZkR9t3Ynj5aW",
                "Other Patient", Role.PATIENT));
        MockHttpSession otherSession = login(other.getUsername(), "patient123");

        mockMvc.perform(get("/patient/cases/" + caseId + "/documents")
                        .session(otherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}