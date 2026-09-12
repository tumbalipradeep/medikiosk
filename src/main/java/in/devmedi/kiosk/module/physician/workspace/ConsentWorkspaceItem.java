package in.devmedi.kiosk.module.physician.workspace;

import java.time.Instant;

/**
 * One consent row shown in the physician consent boundary section.
 */
public record ConsentWorkspaceItem(String type, String name, String state,
                                   String purpose, Instant grantedAt, Instant revokedAt) {
}