package in.devmedi.kiosk.module.his;

/**
 * Boundary between MediKiosk and an external Hospital Information System (HIS)
 * or ABDM endpoint.
 *
 * <p>The current deployment runs in local demo mode and never transmits
 * anything externally. This interface documents where a real HIS/ABDM adapter
 * would plug in: it exposes configuration state so the physician UI can state
 * honestly whether any external transfer is possible, and it exposes a
 * transmission attempt that implementations keep strictly no-op unless a real
 * adapter is configured.</p>
 *
 * <p>Consent in the MediKiosk model governs in-clinic processing only. A real
 * adapter must enforce its own external-sharing authorization before any data
 * leaves MediKiosk; it must never rely on kiosk consent alone.</p>
 */
public interface HisIntegrationBoundary {

    /** Whether an external HIS/ABDM integration is configured (false in demo mode). */
    boolean isConfigured();

    /** Human-readable transport label for the physician UI. */
    String transportLabel();

    /**
     * Attempts to deliver a completed case reference to the external system.
     *
     * <p>The local implementation performs a deterministic no-op (no network
     * call, no credential lookup, no fake success response) and returns
     * {@code false} to indicate that nothing was transmitted. A configured
     * adapter would return {@code true} only when transmission actually
     * happened.</p>
     *
     * @param caseId stable completed-case identity
     * @return whether an external transmission was actually performed
     */
    boolean transmitCompletedCase(String caseId);

    /** Plain-language boundary note for the physician UI. */
    String boundaryNote();
}