package in.devmedi.kiosk.module.physician.workspace;

/**
 * Interoperability boundary status surfaced in the physician workspace so the
 * physician knows what happens when FHIR is exported.
 */
public record InteropBoundaryView(boolean hisConfigured,
                                  boolean externalTransmissionActive,
                                  String transportLabel,
                                  String note) {
}