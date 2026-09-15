package in.devmedi.kiosk.module.ocr;

/**
 * The languages this deployment evaluates for OCR support.
 *
 * <p>Only languages genuinely supported by the selected implementation/provider
 * are reported as supported. With no OCR engine present, every language is
 * {@link OcrProviderStatus#NOT_IMPLEMENTED}.</p>
 */
public enum OcrLanguage {
    ENGLISH("en", "English"),
    HINDI("hi", "Hindi"),
    TELUGU("te", "Telugu");

    private final String code;
    private final String displayName;

    OcrLanguage(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }
}