package in.devmedi.kiosk.module.fhir.service;

import in.devmedi.kiosk.module.document.entity.ClinicalDocument;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FhirDocumentOrderingTest {

    private ClinicalDocument document(String documentId, Instant uploadedAt) {
        ClinicalDocument document = ClinicalDocument.create(documentId, null, null,
                "report.pdf", "stored.pdf", "application/pdf", 1024);
        if (uploadedAt != null) {
            try {
                Field field = ClinicalDocument.class.getDeclaredField("uploadedAt");
                field.setAccessible(true);
                field.set(document, uploadedAt);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot set uploadedAt", e);
            }
        }
        return document;
    }

    @Test
    void ordersByUploadedAtAscending() {
        List<ClinicalDocument> documents = List.of(
                document("doc-c", Instant.parse("2026-09-11T09:00:00Z")),
                document("doc-a", Instant.parse("2026-09-11T07:00:00Z")),
                document("doc-b", Instant.parse("2026-09-11T08:00:00Z")));

        List<String> ordered = documents.stream().sorted(FhirCaseExportService.DOCUMENT_ORDER)
                .map(ClinicalDocument::getDocumentId).toList();

        assertThat(ordered).containsExactly("doc-a", "doc-b", "doc-c");
    }

    @Test
    void breaksUploadTimeTiesByDocumentId() {
        Instant sameInstant = Instant.parse("2026-09-11T07:00:00Z");
        List<ClinicalDocument> documents = List.of(
                document("doc-z", sameInstant),
                document("doc-a", sameInstant),
                document("doc-m", sameInstant));

        List<String> ordered = documents.stream().sorted(FhirCaseExportService.DOCUMENT_ORDER)
                .map(ClinicalDocument::getDocumentId).toList();

        assertThat(ordered).containsExactly("doc-a", "doc-m", "doc-z");
    }

    @Test
    void nullUploadedAtSortsLast() {
        Instant earlier = Instant.parse("2026-09-11T06:00:00Z");
        List<ClinicalDocument> documents = List.of(
                document("doc-null", null),
                document("doc-b", earlier),
                document("doc-a", earlier));

        List<String> ordered = documents.stream().sorted(FhirCaseExportService.DOCUMENT_ORDER)
                .map(ClinicalDocument::getDocumentId).toList();

        assertThat(ordered).containsExactly("doc-a", "doc-b", "doc-null");
        assertThat(FhirCaseExportService.DOCUMENT_ORDER)
                .returns(0, c -> c.compare(document("same", null), document("same", null)));
    }
}