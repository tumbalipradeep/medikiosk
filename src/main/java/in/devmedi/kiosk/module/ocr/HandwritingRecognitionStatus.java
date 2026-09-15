package in.devmedi.kiosk.module.ocr;

/**
 * Terminal state of a handwriting recognition attempt.
 */
public enum HandwritingRecognitionStatus {
    /** A genuine transcript was produced by a real provider. */
    RECOGNIZED,
    /** No handwriting engine exists in this deployment. */
    NOT_IMPLEMENTED,
    /** The provider existed but could not produce a transcript. */
    FAILED
}