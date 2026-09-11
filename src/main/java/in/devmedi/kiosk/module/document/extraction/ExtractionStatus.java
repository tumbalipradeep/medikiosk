package in.devmedi.kiosk.module.document.extraction;

/**
 * Lifecycle status of clinical document text extraction.
 */
public enum ExtractionStatus {

    /** No extraction attempt has been made yet. */
    PENDING,

    /** Text was successfully extracted from the document. */
    EXTRACTED,

    /** Extraction ran but produced no text (e.g. blank or scanned-image PDF). */
    NO_TEXT,

    /** The document type cannot be processed (e.g. image without an OCR engine). */
    UNSUPPORTED,

    /** Extraction failed (malformed, encrypted, unreadable, ...). */
    FAILED
}