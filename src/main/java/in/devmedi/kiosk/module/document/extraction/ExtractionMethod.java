package in.devmedi.kiosk.module.document.extraction;

/**
 * How a document's text was extracted. Provenance only: this records the real
 * path that produced the extraction, never a claim about OCR quality.
 */
public enum ExtractionMethod {
    /** Text extracted from a PDF text layer (PDFBox). */
    PDF_TEXT,
    /** Text extracted from an image via an OCR engine. */
    OCR_IMAGE,
    /** No text extraction was possible (unsupported or never classified). */
    NONE
}