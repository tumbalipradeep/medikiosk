package in.devmedi.kiosk.module.document.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Sniffs the first bytes of an uploaded document to verify that its real
 * binary content matches the declared extension and {@code Content-Type}.
 *
 * <p>The HTTP {@code Content-Type} header is client-supplied and must never be
 * trusted on its own: a file named {@code rx.png} that actually carries HTML or
 * a script would otherwise pass the extension/MIME checks. This validator
 * compares declared metadata against the file's actual magic bytes for the only
 * formats this product accepts (PDF, JPEG, PNG). A mismatch is rejected.</p>
 */
public final class DocumentContentValidator {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final int HEAD_BYTES = 8;

    private DocumentContentValidator() {
    }

    /**
     * Verifies that the uploaded file's magic bytes match the type implied by its
     * file extension and declared content type.
     *
     * @throws IllegalArgumentException when the content cannot be verified
     * @throws IOException              when the file bytes cannot be read
     */
    public static void verifyContent(MultipartFile file, String expectedContentType) throws IOException {
        String expectedMagicKind = magicKindFor(expectedContentType);
        if (expectedMagicKind == null) {
            return;
        }
        byte[] head = readHead(file);
        if (!matches(head, expectedMagicKind)) {
            throw new IllegalArgumentException(
                    "File content does not match its declared type (" + expectedContentType
                            + "); upload rejected as a likely spoofed document");
        }
    }

    private static String magicKindFor(String contentType) {
        if (contentType == null) {
            return null;
        }
        return switch (contentType.toLowerCase()) {
            case "application/pdf" -> "pdf";
            case "image/jpeg" -> "jpeg";
            case "image/png" -> "png";
            default -> null;
        };
    }

    private static byte[] readHead(MultipartFile file) throws IOException {
        byte[] head = new byte[HEAD_BYTES];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(head);
            if (read < 1) {
                throw new IllegalArgumentException("File content cannot be verified; upload rejected");
            }
            if (read < HEAD_BYTES) {
                byte[] trimmed = new byte[read];
                System.arraycopy(head, 0, trimmed, 0, read);
                return trimmed;
            }
        }
        return head;
    }

    private static boolean matches(byte[] head, String kind) {
        return switch (kind) {
            case "pdf" -> startsWith(head, PDF_MAGIC);
            case "jpeg" -> startsWith(head, JPEG_MAGIC);
            case "png" -> startsWith(head, PNG_MAGIC);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] buffer, byte[] prefix) {
        if (buffer.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (buffer[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}