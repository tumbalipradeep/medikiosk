package in.devmedi.kiosk.module.physician.workspace;

import java.util.List;

/**
 * A grouped section of intake answers, preserving the original section labels.
 */
public record WorkspaceSection(String code, String label, List<AnswerEntryView> answers) {
}