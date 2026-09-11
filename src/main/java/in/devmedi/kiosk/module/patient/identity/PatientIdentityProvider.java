package in.devmedi.kiosk.module.patient.identity;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;

/**
 * Resolves a logged-in patient to the identity the kiosk journey operates under.
 *
 * <p>This is the ABHA-ready boundary of the patient experience. Today MediKiosk
 * runs entirely on a local demo identity: it does not connect to ABHA/ABDM, does
 * not know an ABHA number, and never transmits anything off-device. A future
 * provider can be slotted in behind this interface to authenticate a real ABHA
 * number without changing any downstream patient flow - the journey only ever
 * talks to {@link PatientIdentity}.</p>
 */
public interface PatientIdentityProvider {

    /**
     * @return whether this deployment actually links patients to a real ABHA number
     */
    boolean abhaLinked();

    /**
     * Resolves the identity shown during the patient journey.
     *
     * @param user the authenticated patient principal
     * @return the resolved patient identity
     * @throws IllegalArgumentException when the principal is null or not a patient
     */
    PatientIdentity resolve(ApplicationUserDetails user);
}