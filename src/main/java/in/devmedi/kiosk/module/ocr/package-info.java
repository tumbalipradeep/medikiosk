/**
 * OCR and handwriting-recognition provider architecture.
 *
 * <p>Holds the honest capability model ({@code OcrProviderStatus},
 * {@code OcrCapability}, {@code OcrLanguage}), the handwriting-recognition
 * boundary, and a capability registry that reports exactly one status per
 * provider in this deployment. External providers require environment-provided
 * credentials; without them everything reports NOT_IMPLEMENTED and no text is
 * ever fabricated.</p>
 */
package in.devmedi.kiosk.module.ocr;