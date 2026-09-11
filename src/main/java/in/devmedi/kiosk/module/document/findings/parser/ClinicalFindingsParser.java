package in.devmedi.kiosk.module.document.findings.parser;

import in.devmedi.kiosk.module.document.findings.model.StructuredFindings;

/**
 * Strategy for turning extracted document text into structured findings.
 * Implementations must be deterministic: the same text always produces the
 * same {@link StructuredFindings}.
 */
public interface ClinicalFindingsParser {

    StructuredFindings parse(String extractedText);
}