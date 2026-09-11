package in.devmedi.kiosk.module.document.extraction;

/**
 * Machine-readable category for extraction failures or unsupported states.
 */
public enum ExtractionErrorCategory {

    NONE,
    ENCRYPTED,
    MALFORMED,
    UNREADABLE,
    NO_OCR_ENGINE,
    UNSUPPORTED_TYPE,
    UNKNOWN
}