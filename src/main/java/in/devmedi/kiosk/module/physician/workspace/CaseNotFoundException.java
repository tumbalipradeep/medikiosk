package in.devmedi.kiosk.module.physician.workspace;

/**
 * The requested case does not exist.
 */
public class CaseNotFoundException extends RuntimeException {

    public CaseNotFoundException(String caseId) {
        super("Case not found: " + caseId);
    }
}