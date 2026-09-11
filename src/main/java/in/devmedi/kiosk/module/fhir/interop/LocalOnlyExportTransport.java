package in.devmedi.kiosk.module.fhir.interop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local/no-network implementation of {@link FhirExportTransport}. The bundle
 * reaches this boundary but is <b>never transmitted</b>: no network call is
 * made, no filesystem is written to, no ABDM credentials are looked up, and
 * no fake ABDM response is synthesised.
 *
 * <p>The log line records only contract metadata (case id, FHIR version,
 * media type, purpose) - never the generated Bundle itself, clinical answer
 * contents, or document binary. A future ABDM adapter replaces this bean.</p>
 */
@Component
public class LocalOnlyExportTransport implements FhirExportTransport {

    private static final Logger log = LoggerFactory.getLogger(LocalOnlyExportTransport.class);

    @Override
    public void transmit(FhirExportContract contract) {
        log.info("fhir.interop.transport localOnly caseId={} fhirVersion={} mediaType={} purpose={}"
                        + " — bundle reached the export boundary; no data transmitted",
                contract.caseId(), contract.fhirVersion(), contract.mediaType(), contract.purpose());
    }
}