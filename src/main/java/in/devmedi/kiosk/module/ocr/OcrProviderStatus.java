package in.devmedi.kiosk.module.ocr;

/**
 * Honest capability status for every OCR/HWR provider bound in this deployment.
 *
 * <p>Exactly one of these values is reported for every provider; ambiguous
 * wording is not allowed. The status describes what a provider can genuinely
 * do right now in this deployment, not what an interface is shaped to do.</p>
 */
public enum OcrProviderStatus {

    /** A real engine is configured, reachable and its output was verified. */
    REAL_AND_VERIFIED,

    /** The integration is implemented but the environment has not been
     *  credential-verified (no keys/sandbox reachability tested). */
    IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED,

    /** A genuine local deterministic fallback that never invents text. */
    FALLBACK_LOCAL_DETERMINISTIC,

    /** A mock or simulation used only for demos; never clinical text. */
    MOCK_SIMULATION,

    /** No engine is present; the capability is honestly absent. */
    NOT_IMPLEMENTED;

    /**
     * @return whether this status corresponds to a provider that can actually
     *         produce extraction text in this deployment.
     */
    public boolean isOperational() {
        return this == REAL_AND_VERIFIED || this == FALLBACK_LOCAL_DETERMINISTIC;
    }
}