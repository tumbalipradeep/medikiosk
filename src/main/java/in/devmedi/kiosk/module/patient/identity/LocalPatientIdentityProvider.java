package in.devmedi.kiosk.module.patient.identity;

import in.devmedi.kiosk.module.auth.security.ApplicationUserDetails;
import org.springframework.stereotype.Component;

/**
 * Default identity provider for the local demo deployment.
 *
 * <p>Resolves any authenticated patient to a {@link PatientIdentity#local local}
 * identity. This is explicitly a demo/local identity: {@link #abhaLinked()} is
 * always {@code false} and no ABHA number or Aadhaar-derived identifier is
 * ever fabricated. A real deployment would replace this bean with an ABHA
 * provider implementing the same interface.</p>
 */
@Component
public class LocalPatientIdentityProvider implements PatientIdentityProvider {

    @Override
    public boolean abhaLinked() {
        return false;
    }

    @Override
    public PatientIdentity resolve(ApplicationUserDetails user) {
        if (user == null) {
            throw new IllegalArgumentException("A logged-in patient is required to resolve identity");
        }
        String displayName = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername()
                : user.getDisplayName();
        return PatientIdentity.local(displayName);
    }
}