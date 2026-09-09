package in.devmedi.kiosk.module.auth.entity;

public enum Role {
    PATIENT,
    PHYSICIAN,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
