package in.devmedi.kiosk.module.consent.service;

import in.devmedi.kiosk.module.his.HisIntegrationBoundary;
import org.springframework.stereotype.Service;

/**
 * Distinguishes kiosk (in-clinic) consent from external interoperability
 * authorization, and surfaces that boundary state for the physician UI.
 */
@Service
public class ConsentBoundaryService {

    private final HisIntegrationBoundary hisIntegrationBoundary;

    public ConsentBoundaryService(HisIntegrationBoundary hisIntegrationBoundary) {
        this.hisIntegrationBoundary = hisIntegrationBoundary;
    }

    /**
     * Whether any external transmission is actually configured and active.
     *
     * @return always {@code false} in the local demo deployment
     */
    public boolean externalTransmissionActive() {
        return hisIntegrationBoundary.isConfigured();
    }

    /**
     * Plain-language statement of the consent/interoperability boundary.
     */
    public String boundaryNote() {
        return "Kiosk consent covers in-clinic processing only. External data-sharing "
                + "authorization is separate and none is configured in this deployment.";
    }
}