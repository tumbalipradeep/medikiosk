package in.devmedi.kiosk;

import in.devmedi.kiosk.module.document.extraction.ExtractionErrorCategory;
import in.devmedi.kiosk.module.document.extraction.ExtractionResult;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import in.devmedi.kiosk.module.document.extraction.PdfTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTextExtractorTests {

    @TempDir
    Path tempDir;

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void extractsTextFromATextBasedPdf() throws IOException {
        Path pdf = buildTextPdf(1, "Blood pressure measured at 120/80 mm Hg");

        PdfTextExtractor.PdfExtraction result = extractor.extract(pdf);

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTED);
        assertThat(result.errorCategory()).isEqualTo(ExtractionErrorCategory.NONE);
        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.extractedText()).contains("120/80");
        assertThat(result.pages()).hasSize(1);
        assertThat(result.pages().getFirst().pageNumber()).isEqualTo(1);
    }

    @Test
    void multiplePagesPreservePageOrderingInCombinedText() throws IOException {
        Path pdf = buildTextPdf(3, "First page content", "Second page content", "Third page content");

        PdfTextExtractor.PdfExtraction result = extractor.extract(pdf);

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTED);
        assertThat(result.pageCount()).isEqualTo(3);
        assertThat(result.extractedText())
                .startsWith("First page content")
                .endsWith("Third page content")
                .contains("Second page content");
        assertThat(result.extractedText().indexOf("First")).isLessThan(result.extractedText().indexOf("Second"));
        assertThat(result.extractedText().indexOf("Second")).isLessThan(result.extractedText().indexOf("Third"));
        assertThat(result.pages()).extracting(p -> p.pageNumber()).containsExactly(1, 2, 3);
        assertThat(result.pages().get(0).text()).isEqualToNormalizingWhitespace("First page content");
        assertThat(result.pages().get(1).text()).isEqualToNormalizingWhitespace("Second page content");
        assertThat(result.pages().get(2).text()).isEqualToNormalizingWhitespace("Third page content");
    }

    @Test
    void blankPdfWithoutTextIsReportedAsNoText() throws IOException {
        Path pdf = tempDir.resolve("blank.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(pdf.toFile());
        }

        PdfTextExtractor.PdfExtraction result = extractor.extract(pdf);

        assertThat(result.status()).isEqualTo(ExtractionStatus.NO_TEXT);
        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.extractedText()).isEmpty();
    }

    @Test
    void pdfWithZeroPagesIsReportedAsUnreadable() throws IOException {
        Path pdf = tempDir.resolve("empty.pdf");
        try (PDDocument document = new PDDocument()) {
            document.save(pdf.toFile());
        }

        PdfTextExtractor.PdfExtraction result = extractor.extract(pdf);

        assertThat(result.status()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(result.errorCategory()).isEqualTo(ExtractionErrorCategory.UNREADABLE);
    }

    @Test
    void malformedPdfIsReportedAsMalformed() throws IOException {
        Path pdf = tempDir.resolve("malformed.pdf");
        Files.writeString(pdf, "%PDF-1.4 this is not a real pdf at all %%EOF");

        PdfTextExtractor.PdfExtraction result = extractor.extract(pdf);

        assertThat(result.status()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(result.errorCategory()).isEqualTo(ExtractionErrorCategory.MALFORMED);
    }

    @Test
    void nonPdfFileIsReportedAsMalformed() throws IOException {
        Path pdf = tempDir.resolve("fake.pdf");
        Files.writeString(pdf, "just some plain text pretending to be a pdf");

        PdfTextExtractor.PdfExtraction result = extractor.extract(pdf);

        assertThat(result.status()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(result.errorCategory()).isEqualTo(ExtractionErrorCategory.MALFORMED);
    }

    @Test
    void passwordProtectedPdfIsReportedAsEncrypted() throws IOException {
        Path pdf = tempDir.resolve("protected.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            try (PDPageContentStream cs = new PDPageContentStream(document, document.getPage(0))) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Secret content");
                cs.endText();
            }
            AccessPermission permission = new AccessPermission();
            document.protect(new StandardProtectionPolicy("owner-secret", "user-secret", permission));
            document.save(pdf.toFile());
        }

        PdfTextExtractor.PdfExtraction result = extractor.extract(pdf);

        assertThat(result.status()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(result.errorCategory()).isEqualTo(ExtractionErrorCategory.ENCRYPTED);
    }

    @Test
    void extractionNeverMutatesTheSourceFile() throws IOException {
        Path pdf = buildTextPdf(2, "Line one", "Line two");
        byte[] before = Files.readAllBytes(pdf);

        extractor.extract(pdf);

        assertThat(Files.readAllBytes(pdf)).isEqualTo(before);
    }

    private Path buildTextPdf(int pages, String... texts) throws IOException {
        Path pdf = tempDir.resolve("sample-" + pages + "-pages.pdf");
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(texts[i]);
                    cs.endText();
                }
            }
            document.save(pdf.toFile());
        }
        return pdf;
    }
}