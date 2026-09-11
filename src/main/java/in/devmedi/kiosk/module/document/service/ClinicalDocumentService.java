package in.devmedi.kiosk.module.document.service;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.auth.repository.UserRepository;
import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import in.devmedi.kiosk.module.document.repository.ClinicalDocumentRepository;
import in.devmedi.kiosk.module.document.storage.SecureFileStorage;
import in.devmedi.kiosk.module.physician.entity.CompletedCaseEntity;
import in.devmedi.kiosk.module.physician.repository.CompletedCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ClinicalDocumentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".jpg", ".jpeg", ".png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final ClinicalDocumentRepository documentRepository;
    private final CompletedCaseRepository completedCaseRepository;
    private final UserRepository userRepository;
    private final SecureFileStorage fileStorage;

    public ClinicalDocumentService(ClinicalDocumentRepository documentRepository,
                                   CompletedCaseRepository completedCaseRepository,
                                   UserRepository userRepository,
                                   SecureFileStorage fileStorage) {
        this.documentRepository = documentRepository;
        this.completedCaseRepository = completedCaseRepository;
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
    }

    @Transactional
    public ClinicalDocumentMetadata upload(String caseId, Long userId, MultipartFile file) throws IOException {
        validateFile(file);

        CompletedCaseEntity caseEntity = completedCaseRepository.findByCaseId(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        User caseOwner = caseEntity.getUser();
        if (caseOwner == null || !caseOwner.getId().equals(userId)) {
            throw new IllegalArgumentException("You can only upload documents to your own case");
        }

        String storedFilename = SecureFileStorage.generateStoredFilename(file.getOriginalFilename());
        fileStorage.store(file, storedFilename);

        ClinicalDocument doc = ClinicalDocument.create(
                "doc-" + UUID.randomUUID(),
                caseEntity,
                user,
                sanitizeFilename(file.getOriginalFilename()),
                storedFilename,
                file.getContentType(),
                file.getSize());

        try {
            ClinicalDocument saved = documentRepository.save(doc);
            return ClinicalDocumentMetadata.from(saved);
        } catch (RuntimeException ex) {
            try {
                fileStorage.delete(storedFilename);
            } catch (IOException ignored) {
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<ClinicalDocumentMetadata> listByCase(String caseId) {
        return documentRepository.findByCompletedCase_CaseIdOrderByUploadedAtAsc(caseId)
                .stream()
                .map(ClinicalDocumentMetadata::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClinicalDocumentMetadata> listByUserCase(String caseId, Long userId) {
        return documentRepository.findByCompletedCase_CaseIdAndUser_IdOrderByUploadedAtAsc(caseId, userId)
                .stream()
                .map(ClinicalDocumentMetadata::from)
                .toList();
    }

    @Transactional
    public boolean delete(String documentId, Long userId) {
        Optional<ClinicalDocument> docOpt = documentRepository.findByDocumentId(documentId);
        if (docOpt.isEmpty()) {
            return false;
        }
        ClinicalDocument doc = docOpt.get();
        if (!doc.getUser().getId().equals(userId)) {
            return false;
        }
        try {
            fileStorage.delete(doc.getStoredFilename());
        } catch (IOException ignored) {
        }
        documentRepository.delete(doc);
        return true;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds maximum size of 10 MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }

        String ext = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            ext = originalFilename.substring(dotIndex).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: PDF, JPEG, PNG");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported content type. Allowed: PDF, JPEG, PNG");
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "document";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Render-ready metadata for one uploaded clinical document. All fields are
     * plain columns, so no lazy associations are touched while rendering.
     */
    public record ClinicalDocumentMetadata(String documentId,
                                           String originalFilename,
                                           String contentType,
                                           long fileSize,
                                           Instant uploadedAt) {

        public static ClinicalDocumentMetadata from(ClinicalDocument doc) {
            return new ClinicalDocumentMetadata(
                    doc.getDocumentId(),
                    doc.getOriginalFilename(),
                    doc.getContentType(),
                    doc.getFileSize(),
                    doc.getUploadedAt());
        }
    }
}