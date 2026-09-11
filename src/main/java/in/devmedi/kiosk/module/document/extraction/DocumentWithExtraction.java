package in.devmedi.kiosk.module.document.extraction;

import in.devmedi.kiosk.module.document.service.ClinicalDocumentService.ClinicalDocumentMetadata;

/**
 * View pairing of a document's display metadata with its extraction state, used
 * by the physician review page so both render from a single list iteration.
 */
public record DocumentWithExtraction(ClinicalDocumentMetadata document,
                                     ExtractionSummary extraction) {
}