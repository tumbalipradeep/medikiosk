package in.devmedi.kiosk.module.profile.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * Dedicated storage for patient and physician profile pictures.
 *
 * <p>Pictures are stored under a private repository-owned directory using
 * randomly generated filenames; the stored name is the only reference kept in
 * the profile row. Serving is ownership-checked: only the account owner (or an
 * administrator) may read the bytes back.</p>
 */
@Component
public class ProfilePictureStorage {

    private final Path uploadDir;

    public ProfilePictureStorage(@Value("${medikiosk.profile.picture-dir:upload/profile-pictures}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * Persists an image upload under a random name and returns the stored name.
     *
     * @throws IOException when the file cannot be written
     */
    public String store(MultipartFile file, String originalFilename) throws IOException {
        String extension = extensionOf(originalFilename);
        String stored = UUID.randomUUID() + extension;
        Files.createDirectories(uploadDir);
        Path destination = uploadDir.resolve(stored).normalize();
        if (!destination.startsWith(uploadDir)) {
            throw new IOException("Invalid stored filename");
        }
        file.transferTo(destination.toFile());
        return stored;
    }

    /** Resolves a stored name to its absolute path (caller must check ownership). */
    public Path resolve(String storedFilename) {
        return uploadDir.resolve(storedFilename).normalize();
    }

    /**
     * Deletes a stored picture if it exists. Unknown or empty names are no-ops.
     */
    public void delete(String storedFilename) throws IOException {
        if (storedFilename == null || storedFilename.isBlank()) {
            return;
        }
        Path path = resolve(storedFilename);
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return ".png";
        }
        String lower = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (Set.of("jpg", "jpeg", "png", "webp", "gif").contains(lower)) {
            return "." + lower;
        }
        return ".png";
    }

    public static Set<String> allowedImageTypes() {
        return Set.of("image/jpeg", "image/png", "image/webp");
    }

    public static long maxBytes() {
        return 2L * 1024 * 1024;
    }
}