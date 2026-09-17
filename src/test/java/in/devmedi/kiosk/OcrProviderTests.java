package in.devmedi.kiosk;

import in.devmedi.kiosk.module.document.extraction.DocumentTextProcessor;
import in.devmedi.kiosk.module.document.extraction.ExtractionErrorCategory;
import in.devmedi.kiosk.module.document.extraction.ExtractionOutcome;
import in.devmedi.kiosk.module.document.extraction.ExtractionStatus;
import in.devmedi.kiosk.module.document.extraction.LocalDevOcrProvider;
import in.devmedi.kiosk.module.document.extraction.OcrProvider;
import in.devmedi.kiosk.module.document.extraction.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OcrProviderTests {

    @TempDir
    Path tempDir;

    @Test
    void localDevProviderHonestlyReportsThatOcrIsUnavailable() {
        OcrProvider provider = new LocalDevOcrProvider();

        assertThat(provider.isAvailable()).isFalse();
        assertThat(provider.engineName()).contains("no OCR engine");
    }

    @Test
    void localDevProviderNeverFabricatesClinicalText() {
        BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
        // Draw nothing - even a blank image must never produce fabricated text.
        OcrProvider provider = new LocalDevOcrProvider();

        ExtractionOutcome outcome = provider.extract(image);

        assertThat(outcome.status()).isEqualTo(ExtractionStatus.UNSUPPORTED);
        assertThat(outcome.errorCategory()).isEqualTo(ExtractionErrorCategory.NO_OCR_ENGINE);
        assertThat(outcome.extractedText()).isNull();
        assertThat(outcome.errorMessage()).containsIgnoringCase("OCR engine is not available");
    }

    @Test
    void imageDocumentsAreReportedAsUnsupportedWithoutAnOcrEngine() throws IOException {
        Path image = tempDir.resolve("rx-scan.png");
        BufferedImage buffered = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(buffered, "png", image.toFile());

        DocumentTextProcessor processor = new DocumentTextProcessor(
                new PdfTextExtractor(),
                List.of(new LocalDevOcrProvider()),
                new in.devmedi.kiosk.module.ocr.OcrProperties("local-dev", null, 0));

        ExtractionOutcome outcome = processor.process(image, "image/png");

        assertThat(outcome.status()).isEqualTo(ExtractionStatus.UNSUPPORTED);
        assertThat(outcome.errorCategory()).isEqualTo(ExtractionErrorCategory.NO_OCR_ENGINE);
        assertThat(outcome.extractedText()).isNull();
    }

    @Test
    void jpegDocumentsAreAlsoReportedAsUnsupportedWithoutAnOcrEngine() throws IOException {
        Path image = tempDir.resolve("rx.jpg");
        BufferedImage buffered = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(buffered, "jpg", image.toFile());

        DocumentTextProcessor processor = new DocumentTextProcessor(
                new PdfTextExtractor(),
                List.of(new LocalDevOcrProvider()),
                new in.devmedi.kiosk.module.ocr.OcrProperties("local-dev", null, 0));

        ExtractionOutcome outcome = processor.process(image, "image/jpeg");

        assertThat(outcome.status()).isEqualTo(ExtractionStatus.UNSUPPORTED);
        assertThat(outcome.errorCategory()).isEqualTo(ExtractionErrorCategory.NO_OCR_ENGINE);
    }

    @Test
    void unsupportedContentTypesAreRejectedHonestly() throws IOException {
        DocumentTextProcessor processor = new DocumentTextProcessor(
                new PdfTextExtractor(),
                List.of(new LocalDevOcrProvider()),
                new in.devmedi.kiosk.module.ocr.OcrProperties("local-dev", null, 0));

        ExtractionOutcome outcome = processor.process(tempDir.resolve("whatever.bin"), "application/gzip");

        assertThat(outcome.status()).isEqualTo(ExtractionStatus.UNSUPPORTED);
        assertThat(outcome.errorCategory()).isEqualTo(ExtractionErrorCategory.UNSUPPORTED_TYPE);
    }
}