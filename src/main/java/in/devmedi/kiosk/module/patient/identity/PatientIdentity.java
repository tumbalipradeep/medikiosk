package in.devmedi.kiosk.module.patient.identity;

/**
 * The patient identity shown during the kiosk journey.
 *
 * <p>An honest, minimal identity bound to the current signing mechanism. In
 * demo mode there is intentionally no ABHA number or Aadhaar-derived
 * identifier: those must never be invented or fabricated, so the record only
 * carries what is actually known.</p>
 *
 * @param displayName patient facing display name
 * @param mode        identity mode label (for example {@code LOCAL}/{@code DEMO})
 * @param label       short human-readable identity label
 * @param summary     plain-language explanation shown to the patient
 * @param abhaLinked  whether the identity is linked to a real ABHA number
 * @param abhaNumber  the real ABHA number, or {@code null} when not linked
 */
public record PatientIdentity(String displayName,
                              String mode,
                              String label,
                              String summary,
                              boolean abhaLinked,
                              String abhaNumber) {

    public static PatientIdentity local(String displayName) {
        return new PatientIdentity(
                displayName,
                "LOCAL",
                "Local kiosk identity",
                "This device is running in demo mode. Your answers stay on this kiosk and are "
                        + "not linked to an ABHA (Ayushman Bharat Health Account) or sent to ABDM.",
                false,
                null);
    }
}