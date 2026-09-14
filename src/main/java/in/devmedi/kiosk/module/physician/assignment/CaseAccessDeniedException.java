package in.devmedi.kiosk.module.physician.assignment;

/**
 * Thrown when a physician attempts to open a case that currently has an
 * active assignment for another physician. Mapped to HTTP 403 Forbidden.
 */
public class CaseAccessDeniedException extends RuntimeException {

    public CaseAccessDeniedException(String caseId) {
        super("This case is assigned to another physician: " + caseId);
    }
}