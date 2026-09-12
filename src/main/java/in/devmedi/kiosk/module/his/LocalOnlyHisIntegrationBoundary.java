package in.devmedi.kiosk.module.his;

import org.springframework.stereotype.Component;

/**
 * Deterministic no-op HIS boundary for the local demo deployment.
 *
 * <p>Never transmits anything, never looks up credentials, and never produces a
 * fake ABDM or HIS success response. Its {@code transmitCompletedCase} always
 * returns {@code false} so callers can never mistake a no-op for a successful
 * external transfer.</p>
 */
@Component
public class LocalOnlyHisIntegrationBoundary implements HisIntegrationBoundary {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String transportLabel() {
        return "Local-only (no external transmission)";
    }

    @Override
    public boolean transmitCompletedCase(String caseId) {
        return false;
    }

    @Override
    public String boundaryNote() {
        return "FHIR export is a local clinical projection. No ABDM or external HIS "
                + "transmission is configured. Kiosk consent does not constitute "
                + "external data-sharing authorization.";
    }
}