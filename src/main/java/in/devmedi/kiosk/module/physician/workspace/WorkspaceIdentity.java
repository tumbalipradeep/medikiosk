package in.devmedi.kiosk.module.physician.workspace;

/**
 * Patient identity for the physician case workspace, built from the linked
 * patient user (if present) and the fixed local/demo identity note.
 */
public record WorkspaceIdentity(String patientLabel,
                                boolean patientLinked,
                                String identityMode,
                                String identityLabel,
                                String identitySummary,
                                boolean abhaLinked) {

    public static WorkspaceIdentity linked(String label) {
        return new WorkspaceIdentity(label, true, "LOCAL", "Local kiosk identity",
                "This device is running in demo mode. The patient identity is local and not "
                        + "linked to an ABHA (Ayushman Bharat Health Account).", false);
    }

    public static WorkspaceIdentity unlinked() {
        return new WorkspaceIdentity("Anonymous", false, "LOCAL", "Local kiosk identity",
                "This device is running in demo mode. The patient identity is local and not "
                        + "linked to an ABHA (Ayushman Bharat Health Account).", false);
    }
}