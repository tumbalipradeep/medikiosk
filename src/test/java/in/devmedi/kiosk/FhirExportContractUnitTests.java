package in.devmedi.kiosk;

import in.devmedi.kiosk.module.fhir.interop.FhirExportContract;
import in.devmedi.kiosk.module.fhir.model.FhirBundle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FhirExportContract}. The contract is a small immutable
 * record that carries identifiers and the FHIR Bundle reference to the
 * interoperability boundary without copying or re-encoding the Bundle model.
 */
class FhirExportContractUnitTests {

    @Test
    void ofPreservesBundleInstanceIdentity() {
        FhirBundle bundle = FhirBundle.collection("bundle-1", "2025-01-01T00:00:00Z", List.of());
        FhirExportContract contract = FhirExportContract.of("case-1", bundle);

        assertThat(contract.bundle()).isSameAs(bundle);
        assertThat(contract.caseId()).isEqualTo("case-1");
        assertThat(contract.resourceType()).isEqualTo("Bundle");
    }

    @Test
    void fixedContractMetadataIsCorrect() {
        FhirBundle bundle = FhirBundle.collection("id", "ts", List.of());
        FhirExportContract contract = FhirExportContract.of("case-x", bundle);

        assertThat(contract.fhirVersion()).isEqualTo("4.0.1");
        assertThat(contract.mediaType()).isEqualTo("application/fhir+json");
        assertThat(contract.purpose()).isEqualTo(FhirExportContract.PURPOSE_CLINICAL_EXPORT);
        assertThat(contract.resourceType()).isEqualTo("Bundle");
    }

    @Test
    void resourceTypeIsDerivedFromBundleNotHardcoded() {
        FhirBundle bundle = FhirBundle.collection("b", "2025-01-01T00:00:00Z", List.of());
        FhirExportContract contract = FhirExportContract.of("case-1", bundle);
        assertThat(contract.resourceType()).isEqualTo(bundle.resourceType());
    }
}