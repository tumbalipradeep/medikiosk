package in.devmedi.kiosk.module.fhir.mapping;

import in.devmedi.kiosk.module.auth.entity.User;
import in.devmedi.kiosk.module.fhir.id.FhirIds;
import in.devmedi.kiosk.module.fhir.model.FhirHumanName;
import in.devmedi.kiosk.module.fhir.model.FhirIdentifier;
import in.devmedi.kiosk.module.fhir.model.FhirPatient;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps a kiosk {@link User} (role PATIENT) to a FHIR R4 {@code Patient}.
 *
 * <p>Only persisted identity is projected: the unique username as an
 * identifier and the display name as the name text. Gender and date of birth
 * are not stored anywhere in MediKiosk and are therefore never emitted.</p>
 */
public final class FhirPatientMapper {

    /** Username identifier system (the only persisted stable patient identifier). */
    public static final String USERNAME_SYSTEM = "urn:medikiosk:username";

    private FhirPatientMapper() {
    }

    public static FhirPatient toFhir(User user) {
        String username = user.getUsername();
        List<FhirIdentifier> identifiers = new ArrayList<>();
        if (username != null && !username.isBlank()) {
            identifiers.add(FhirIdentifier.of(USERNAME_SYSTEM, username));
        }
        List<FhirHumanName> names = new ArrayList<>();
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            names.add(FhirHumanName.of(user.getDisplayName()));
        }
        return FhirPatient.of(FhirIds.patient(username), user.isEnabled(), identifiers, names);
    }
}