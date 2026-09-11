package in.devmedi.kiosk.module.fhir.interop;

/**
 * Interoperability transmission port for FHIR exports. Implementations are
 * responsible for delivering an {@link FhirExportContract} to an external
 * party.
 *
 * <p>Current implementation ({@link LocalOnlyExportTransport}) deliberately
 * does <b>not</b> transmit anything. A future ABDM adapter replaces that bean
 * with a real implementation, looks for ABDM credentials independently, and
 * enforces its own consent policy before any data leaves MediKiosk. The
 * application carries no ABDM credential configuration today.</p>
 *
 * <p>Consent in the MediKiosk model (the {@code Consent} entity / FHIR
 * {@code Consent} projection) governs in-clinic processing only. It is
 * <b>not</b> a grant of authority to transmit PHI to ABDM. A future ABDM
 * adapter must not rely on this interface alone for consent-gated
 * transmission.</p>
 */
public interface FhirExportTransport {

    /** Transmits (or drops) a generated export contract. Must not throw. */
    void transmit(FhirExportContract contract);
}