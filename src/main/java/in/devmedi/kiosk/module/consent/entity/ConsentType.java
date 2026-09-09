package in.devmedi.kiosk.module.consent.entity;

/**
 * Granular consent types a patient may grant or revoke.
 */
public enum ConsentType {
    CLINICAL_CASE_TAKING,
    AUDIO_CAPTURE,
    DOCUMENT_PROCESSING,
    DATA_SHARING;

    public String readableName() {
        return switch (this) {
            case CLINICAL_CASE_TAKING -> "Clinical Case-Taking";
            case AUDIO_CAPTURE -> "Audio Capture";
            case DOCUMENT_PROCESSING -> "Document Processing";
            case DATA_SHARING -> "Data Sharing";
        };
    }
}
