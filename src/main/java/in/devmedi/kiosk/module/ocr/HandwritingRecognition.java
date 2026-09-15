package in.devmedi.kiosk.module.ocr;

/**
 * Immutable result of a handwriting recognition attempt.
 *
 * <p>Text is only ever present when the underlying provider honestly recognised
 * handwriting. A provider in {@link OcrProviderStatus#NOT_IMPLEMENTED} state
 * never fabricates a transcript.</p>
 *
 * @param status   whether a genuine transcript was produced
 * @param text     recognised text, or {@code null} when none
 * @param language source language, or {@code null} when unknown
 * @param note     honest provenance/limitation note
 */
public record HandwritingRecognition(HandwritingRecognitionStatus status,
                                     String text,
                                     OcrLanguage language,
                                     String note) {

    public static HandwritingRecognition notImplemented(String note) {
        return new HandwritingRecognition(HandwritingRecognitionStatus.NOT_IMPLEMENTED, null, null, note);
    }
}