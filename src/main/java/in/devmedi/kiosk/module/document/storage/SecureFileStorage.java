package in.devmedi.kiosk.module.document.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class SecureFileStorage {

    private final Path uploadDir;

    public SecureFileStorage(@Value("${medikiosk.documents.upload-dir:upload/clinical-documents}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path store(MultipartFile file, String storedFilename) throws IOException {
        Files.createDirectories(uploadDir);
        Path destination = uploadDir.resolve(storedFilename).normalize();
        if (!destination.startsWith(uploadDir)) {
            throw new IOException("Invalid stored filename");
        }
        file.transferTo(destination.toFile());
        return destination;
    }

    public Path resolve(String storedFilename) {
        return uploadDir.resolve(storedFilename).normalize();
    }

    public void delete(String storedFilename) throws IOException {
        Path path = resolve(storedFilename);
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

    public static String generateStoredFilename(String originalFilename) {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        }
        return UUID.randomUUID() + ext;
    }
}
