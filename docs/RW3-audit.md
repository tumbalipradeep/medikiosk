# RW3 Audit — Document Intelligence & Hardening

**Milestone:** M7 / RW3 · **Repository:** devmedi/medikiosk · **Branch:** master

RW3 closes the document-intelligence gap left by RW2 with an honest,
deterministic, and test-covered layer. The guiding rule of every phase is the
same as RW1/RW2: **never fabricate OCR, handwriting-recognition, or AI
capability, never invent data, and never weaken security.**

This document records what was delivered, what is deliberately *not* claimed,
and how each claim is verified.

---

## 1. Baseline

- Commit before RW3: `c489f18` ("Add registration regression coverage").
- Existing suite at baseline: ~84 test classes / ~708 tests.
- `module/ocr` was a placeholder; `LocalDevOcrProvider` honestly returned
  `UNSUPPORTED` / `NO_OCR_ENGINE`.
- Upload validation was extension + Content-Type only (no magic-byte sniffing).
- Timeline covered only `DOCUMENT_UPLOADED`, `REPORT_DATE`, `ENCOUNTER_DATE`,
  `VITALS`, `LAB_RESULT`, `MEDICATION`.
- Lab classification already used `AbnormalityStatus` LOW/NORMAL/HIGH/UNKNOWN
  with a conservative evaluator that never supplies a range the document lacks.

## 2. What was delivered

### 2.1 Content (magic-byte) validation — `module/document`
- `storage/DocumentContentValidator` sniffs real magic bytes:
  - PDF  → `%PDF-` (0x25 0x50 0x44 0x46 0x2D)
  - JPEG → `FF D8 FF` (with a tolerant follow-up byte check)
  - PNG  → `89 50 4E 47 0D 0A 1A 0A`
- `ClinicalDocumentService.validateFile` enforces extension ↔ Content-Type
  consistency **and** magic bytes. A spoofed file (rename `.pdf` to `.jpg` with
  `image/jpeg` header) is rejected with `400` even when the headers agree.
- Existing fixtures that previously used fake bytes now use real magic bytes so
  the validation actually runs in the regression suite.

### 2.2 OCR capability architecture — `module/ocr`
- `OcrProviderStatus`: `REAL_AND_VERIFIED`,
  `IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED`, `FALLBACK_LOCAL_DETERMINISTIC`,
  `MOCK_SIMULATION`, `NOT_IMPLEMENTED`. Only the first is `isOperational()`.
- `OcrLanguage`: English (`en`), Hindi (`hi`), Telugu (`te`).
- `OcrProperties` (`@ConfigurationProperties(prefix = "medikiosk.ocr")`,
  registered via `SecurityConfig` `@EnableConfigurationProperties`):
  provider `local-dev` by default, timeout 15000 ms, languages en/hi/te.
- `OcrProviderStatusSource` implemented by `LocalDevOcrProvider`, which now
  reports `NOT_IMPLEMENTED` — it honestly states no OCR engine is present.
- `OcrCapabilityService` + `OcrCapabilitiesResponse` / `HwrCapabilityResponse`
  build the report deterministically; if the configured provider is not bound,
  a deterministic fallback is chosen and a `fallbackNote` explains it.
- Endpoints (public, no patient data):
  - `GET /api/capabilities/ocr`
  - `GET /api/capabilities/hwr`

### 2.3 Handwriting-recognition seam — `module/ocr`
- `HandwritingRecognition`, `HandwritingRecognitionStatus`
  (`RECOGNIZED` / `NOT_IMPLEMENTED` / `FAILED`),
  `HandwritingRecognitionProvider`,
  `NotImplementedHwrProvider` (engine `none`, status `NOT_IMPLEMENTED`).
- Explicit contract: ordinary OCR output is never relabelled as handwriting
  recognition.

### 2.4 Extraction provenance — `V24__rw3_extraction_provenance.sql`
- New columns on `clinical_document_extractions`:
  `extraction_method VARCHAR(24)`, `provider_name VARCHAR(60)`,
  `source_language VARCHAR(8)` (all nullable).
- `ExtractionMethod` enum: `PDF_TEXT`, `OCR_IMAGE`, `NONE`.
- `ClinicalDocumentExtraction.recordProvenance(...)`; the extraction service
  records `PDF_TEXT`→`pdfbox`, `OCR_IMAGE`→engine name (`… (not available)`
  when no engine is bound), `NONE`→null.
- Provenance is surfaced in `DocumentDetailResponse`, `DocumentWorkspaceItem`,
  the physician workspace table (`case.html`) and the document details modal
  (`physician.js`).
- Existing `ExtractionOutcome` is intentionally unchanged because multiple
  tests construct it with its current 6-argument shape.

### 2.5 Timeline enrichment — `module/physician/service/PhysicianTimelineService`
New deterministic `TimelineEventType` values, all derived from persisted data
only:

| Type | Source data |
|------|-------------|
| `EXTRACTION_COMPLETED` | `clinical_document_extractions` (status, method, provider, pages, extractedAt) |
| `ABNORMAL_LAB_DETECTED` | findings labs with `LOW` / `HIGH` (value, unit, range, snippet) |
| `PHYSICIAN_REVIEW` | `physician_review_entries` (+ original answer text from `completed_case_answers`) |
| `ENCOUNTER_CREATED` | encounter row with `submittedAt` (intake submitted) |
| `CONSULTATION_FINALIZED` | finalized consultation (assessment/plan excerpt, finalizedAt) |

Non-document events carry `sourceDocumentFilename = "case record"` so the
timeline renders cleanly without faking a document source. Ordering contract is
unchanged: dated events first (chronological), then undated by sort instant →
document order → occurrence index, with no invented dates.

### 2.6 Conservative lab classification (confirmed, locked)
- A lab value with **no printed reference range** is `UNKNOWN` — never judged
  abnormal, never given an invented range, never called `CRITICAL`. Locked by
  unit tests (`LabAbnormalityEvaluatorTests`) and end-to-end
  (`ClinicalFindingsAnalysisIntegrationTests`, TSH line printed without a
  range → `UNKNOWN`).

## 3. Honest boundaries (deliberately NOT claimed)

- **No OCR engine**: image text extraction is `NOT_IMPLEMENTED`; a scanned PDF
  yields `NO_TEXT`, not a fake transcript. Capability endpoint says so publicly.
- **No handwriting recognition**: `NOT_IMPLEMENTED`; not relabelled as OCR.
- **No AI/LLM in extraction**: text extraction uses PDFBox only.
- **No invented lab ranges or CRITICAL grades**: out of scope by design.
- **No fabricated document provenance**: `provider_name`/`extraction_method`
  come from the actual extraction path.

## 4. Verification

### 4.1 Test suite (full clean regression — PASS)
```
mvn -q clean test
```
Result: **85 test classes / 718 tests, 0 failures, 0 errors.**

New/updated coverage:
- `ClinicalDocumentIntegrationTests` (18 tests) — magic-byte acceptance and
  rejection, spoofed MIME files, cross-patient upload rejection and empty
  listing.
- `PhysicianTimelineIntegrationTests` (11 tests) — timeline reordering,
  extraction/abnormal events, physician-review + consultation-finalized events,
  provenance in detail and workspace.
- `LabAbnormalityEvaluatorTests` (18 tests) — conservative unknown handling for
  value-without-range.
- `CapabilityStatusIntegrationTests` (3 tests) — anonymous, honest,
  deterministic OCR/HWR capability responses.
- `PhysicianReviewControllerIntegrationTests` — real magic bytes fixture.

### 4.2 Bounded HTTP verification (PASS)
Fresh boot of `spring-boot:run` on an in-memory
PostgreSQL-compatible H2 database (isolated on port 18080), then read-only
probes:

| Probe | Result |
|-------|--------|
| `GET /api/capabilities/ocr` | 200 — provider `local-dev`, `NOT_IMPLEMENTED`, en/hi/te all `NOT_IMPLEMENTED` |
| `GET /api/capabilities/hwr` | 200 — provider `none`, `NOT_IMPLEMENTED`, honest note |
| `GET /login` | 200 — rendered sign-in page |
| `GET /patient/cases` (anonymous) | 302 — redirect to login (security intact) |

The verification instance was stopped afterwards.

## 5. Files changed (RW3)

New (main):
- `module/document/storage/DocumentContentValidator.java`
- `module/ocr/`: `OcrProviderStatus.java`, `OcrLanguage.java`,
  `OcrCapability.java`, `OcrProperties.java`, `OcrProviderStatusSource.java`,
  `OcrCapabilityService.java`, `HandwritingRecognition.java`,
  `HandwritingRecognitionStatus.java`, `HandwritingRecognitionProvider.java`,
  `NotImplementedHwrProvider.java`, `controller/CapabilityStatusController.java`
- `module/document/extraction/ExtractionMethod.java`
- `db/migration/V24__rw3_extraction_provenance.sql`

Modified (main):
- `module/document/service/ClinicalDocumentService.java`
- `module/document/extraction/LocalDevOcrProvider.java`,
  `DocumentTextProcessor.java`, `ExtractionResult.java`
- `module/document/entity/ClinicalDocumentExtraction.java`
- `module/document/service/ClinicalDocumentExtractionService.java`
- `module/document/findings/DocumentDetailResponse.java`,
  `DocumentWorkspaceItem.java`, `model/TimelineEventType.java`
- `module/physician/service/PhysicianTimelineService.java`
- `config/SecurityConfig.java`
- `application.yml`, `module/ocr/package-info.java`
- `templates/physician/case.html`, `static/js/physician.js`

New/updated (tests):
- `CapabilityStatusIntegrationTests.java`
- `ClinicalDocumentIntegrationTests.java`, `PhysicianTimelineIntegrationTests.java`
- `LabAbnormalityEvaluatorTests.java`, `PhysicianReviewControllerIntegrationTests.java`

## 6. Known gaps (unchanged, tracked)

- Real OCR / HWR require external engines + credential verification; the seam
  and capabilities report are ready and honest but the engines are not shipped.
- Browser-level E2E automation was not available in this environment; HTTP
  verification is via the MockMvc suite plus bounded live probes.