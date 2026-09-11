package in.devmedi.kiosk.module.document.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Text extractor for text-based PDFs using Apache PDFBox.
 *
 * <p>Operates on the secure local copy identified by a stored filename and never
 * modifies the original upload. Encrypted, malformed and unreadable PDFs are
 * reported as failed outcomes with a machine-readable error category. A PDF
 * whose pages carry no extractable text (e.g. scanned images) is reported as
 * {@link ExtractionStatus#NO_TEXT} rather than inventing OCR content.</p>
 */
@Component
public class PdfTextExtractor {

    public PdfExtraction extract(Path pdfPath) {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            if (document.isEncrypted()) {
                return PdfExtraction.failed(ExtractionErrorCategory.ENCRYPTED,
                        "The PDF is encrypted or password-protected");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                return PdfExtraction.failed(ExtractionErrorCategory.UNREADABLE,
                        "The PDF contains no pages");
            }

            List<ExtractionResult.PageText> pages = new ArrayList<>(pageCount);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                pages.add(new ExtractionResult.PageText(pageNumber, stripper.getText(document)));
            }

            String combined = pages.stream()
                    .map(ExtractionResult.PageText::text)
                    .map(String::trim)
                    .filter(pageText -> !pageText.isEmpty())
                    .collect(Collectors.joining("\n"));

            if (combined.isBlank()) {
                return new PdfExtraction(ExtractionStatus.NO_TEXT, ExtractionErrorCategory.NONE,
                        "No extractable text found - the PDF may contain only scanned images",
                        "", pageCount, pages);
            }
            return new PdfExtraction(ExtractionStatus.EXTRACTED, ExtractionErrorCategory.NONE,
                    null, combined, pageCount, pages);
        } catch (InvalidPasswordException ex) {
            return PdfExtraction.failed(ExtractionErrorCategory.ENCRYPTED,
                    "The PDF is encrypted or password-protected");
        } catch (IOException ex) {
            return PdfExtraction.failed(ExtractionErrorCategory.MALFORMED,
                    "The PDF could not be read or is malformed");
        } catch (RuntimeException ex) {
            return PdfExtraction.failed(ExtractionErrorCategory.MALFORMED,
                    "The PDF could not be parsed");
        }
    }

    public ExtractionOutcome toOutcome(PdfExtraction extraction) {
        return new ExtractionOutcome(extraction.status(), extraction.errorCategory(),
                extraction.errorMessage(), extraction.extractedText(),
                extraction.pageCount(), extraction.pages());
    }

    public record PdfExtraction(ExtractionStatus status,
                                ExtractionErrorCategory errorCategory,
                                String errorMessage,
                                String extractedText,
                                int pageCount,
                                List<ExtractionResult.PageText> pages) {

        private static PdfExtraction failed(ExtractionErrorCategory category, String message) {
            return new PdfExtraction(ExtractionStatus.FAILED, category, message, null, 0, List.of());
        }
    }
}