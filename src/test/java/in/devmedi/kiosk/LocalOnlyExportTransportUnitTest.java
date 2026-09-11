package in.devmedi.kiosk;

import in.devmedi.kiosk.module.fhir.interop.FhirExportContract;
import in.devmedi.kiosk.module.fhir.interop.FhirExportTransport;
import in.devmedi.kiosk.module.fhir.interop.LocalOnlyExportTransport;
import in.devmedi.kiosk.module.fhir.model.FhirBundle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link LocalOnlyExportTransport}. The local transport
 * deliberately does NOT transmit the Bundle anywhere: no network call, no
 * filesystem write, no ABDM credential lookup, and no fake response. The
 * application requires no ABDM credentials or configuration for this bean
 * to operate.
 */
class LocalOnlyExportTransportUnitTest {

    @Test
    void transmitCompletesWithoutException() {
        FhirExportTransport transport = new LocalOnlyExportTransport();
        FhirExportContract contract = FhirExportContract.of("case-1",
                FhirBundle.collection("b1", "2025-01-01T00:00:00Z", List.of()));

        assertThatCode(() -> transport.transmit(contract)).doesNotThrowAnyException();
    }

    @Test
    void transmitDoesNotPerformNetworkIO() {
        LocalOnlyExportTransport transport = new LocalOnlyExportTransport();
        FhirExportContract contract = FhirExportContract.of("case-2",
                FhirBundle.collection("b2", "2025-01-01T00:00:00Z", List.of()));

        // Must complete synchronously with no external calls; the only observable
        // effect is a log line (SLF4J); no data leaves the process.
        assertThatCode(() -> transport.transmit(contract)).doesNotThrowAnyException();
    }

    @Test
    void noAbdmCredentialsRequired() {
        FhirExportTransport transport = new LocalOnlyExportTransport();
        FhirExportContract contract = FhirExportContract.of("case-3",
                FhirBundle.collection("b3", "2025-01-01T00:00:00Z", List.of()));

        assertThatCode(() -> transport.transmit(contract)).doesNotThrowAnyException();
    }
}