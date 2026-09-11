package in.devmedi.kiosk.module.document.findings.model;

/**
 * Availability of drug-interaction analysis for an extraction.
 *
 * <p>No reliable interaction dataset ships with this deployment, so interaction
 * analysis is explicitly reported as unavailable rather than faked from a
 * made-up registry.</p>
 */
public enum InteractionAnalysisStatus {
    NOT_AVAILABLE
}