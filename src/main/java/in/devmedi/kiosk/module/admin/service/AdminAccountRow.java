package in.devmedi.kiosk.module.admin.service;

import java.time.Instant;

/**
 * One row of the administrator account list. {@code detail} carries a short,
 * role-appropriate descriptor (department / organization for physicians,
 * preferred language for patients) and is always derived from persisted
 * profile data - never invented.
 */
public record AdminAccountRow(Long id,
                              String username,
                              String displayName,
                              String role,
                              boolean enabled,
                              boolean locked,
                              boolean mustChangePassword,
                              Instant createdAt,
                              String detail) {

    public String statusLabel() {
        if (!enabled) {
            return "Deactivated";
        }
        if (locked) {
            return "Locked";
        }
        if (mustChangePassword) {
            return "Password change required";
        }
        return "Active";
    }

    public String statusCss() {
        if (!enabled) {
            return "text-bg-secondary";
        }
        if (locked) {
            return "text-bg-danger";
        }
        if (mustChangePassword) {
            return "text-bg-warning";
        }
        return "text-bg-success";
    }
}
