# RD3 Audit — Homepage, printed-document OCR, and integration boundaries

Scope of this document: what RD3 actually implemented and verified on top of
the RD2 checkpoint (`3c989d9`). Facts only; no claimed integrations.

## 1. Homepage redesign (implemented, verified)

- **Derived capability rows.** Every capability row on the landing page is now
  produced by the running application from its authoritative services
  (`OcrCapabilityService`, HWR capability, `AiFailoverService`,
  `VoiceProperties`, `HisIntegrationBoundary`). The previously hard-coded
  OCR/voice/HIS rows now render the actual deployment state:
  - OCR: `IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED` when the Bhashini engine is
    wired but unverified, `NOT_IMPLEMENTED` for the pure local-dev state,
    operational only when a verified engine is configured.
  - Voice: "Configured" only when the ASR/TTS provider switch + credentials are
    complete; otherwise "Depending on key".
  - HWR: always honest — `NOT_IMPLEMENTED` unless a real HWR provider exists.
  - HIS/ABDM: "Local only" until a configured boundary exists.
- **Role entry points.** Dedicated patient / physician / administrator cards
  with real, secured routes (`/login`, `/register`); no dead links.
- **Copy and hierarchy.** Hero lead, journey steps, and trust list rewritten to
  describe the shipped product (patient corrections, provenance, review flow).
- **Preserved.** Canonical logo/branding, RD2 contracts (`appStatusLive`
  binding, `/actuator/health` absolute fetch, AI badge derivation), and the
  role-redirect behavior for authenticated users.
- Tests: `PublicPagesIntegrationTests.landingCapabilityRowsAreDerivedFromAuthoritativeServices`
  pins that non-operational OCR never renders a "Live" claim and that the voice
  row agrees with `VoiceProperties`.

## 2. Printed-document OCR (implemented, credential-gated, NOT live)

**What was genuinely missing:** the `OcrProvider` seam existed but the only
bound engine was `LocalDevOcrProvider` (`isAvailable() = false`, honest
`NO_OCR_ENGINE`), so image uploads could never be extracted.

**What RD3 added:**

- `BhashiniOcrProvider` — printed-text OCR through the Bhashini/ULCA pipeline,
  reusing the existing, credential-gated `BhashiniGateway` (the same one the
  voice layer uses; a new `ocr` task method was added following the provider's
  documented two-round-trip flow: config call → compute call).
- `DocumentTextProcessor` now resolves the configured engine from all bound
  engines (`medikiosk.ocr.provider`, same matching rule as the capability
  report) instead of hard-wiring one.
- Extracted text flows through the existing provenance pipeline unchanged:
  method `OCR_IMAGE`, provider name, page-level text, physician review
  required. No code path bypasses human review.

**Honesty rules enforced in code and tests:**

- Status is `IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED` — even with complete
  credentials — because this deployment has not verified the pipeline end to
  end. It is never `REAL_AND_VERIFIED` from configuration alone.
- Without credentials the provider is unavailable and refuses extraction
  (no fabricated text). Provider failures map to `FAILED`/`NO_TEXT`
  outcomes, never to invented clinical content.
- Public `/api/capabilities/ocr` now lists both engines with their truthful
  statuses; the overall status still follows the *configured* provider
  (`local-dev` → `NOT_IMPLEMENTED` in the default deployment).

**Blocker (truthful):** no Bhashini/ULCA credentials or sandbox pipeline ID are
available in this session, so the OCR path could NOT be verified against the
live service. Enabling it requires `MEDIKIOSK_BHASHINI_USER_ID`,
`MEDIKIOSK_BHASHINI_API_KEY`, a pipeline with OCR tasks, and
`MEDIKIOSK_OCR_PROVIDER=bhashini` — followed by an operator verification pass
before any "live" claim. Until then the UI, API, and admin console all report
the unverified state.

## 3. Handwriting recognition (unchanged, honestly absent)

No suitable HWR provider exists in the allowed public-apis catalogue, and
ordinary OCR must never be relabelled as HWR. `NotImplementedHwrProvider`
remains the only bound provider; `/api/capabilities/hwr`, the landing page,
and the admin console all continue to report `NOT_IMPLEMENTED`. Blocker:
no genuine handwriting provider or credentials; not faked.

## 4. ABDM / HIS integration (assessment; no transmission implemented)

Actual requirements for a live ABDM linkage (from the existing boundary
contracts and ABDM building blocks):

1. **Facility/HIP registration** with the ABDM sandbox and a registered
   HIP ID — cannot be done from this session.
2. **ABHA number/linking flow** for patients (ABHA creation, demographic
   auth, care-context linking) — a separate, credential-gated workflow.
3. **Consent-manager artefacts** (consent request/notification handling);
   the existing MediKiosk `Consent` model explicitly does NOT grant
   external-transmission authority (documented on `FhirExportTransport`).
4. **Gateway credentials and certificates** for HIP endpoints.
5. **A real `FhirExportTransport` implementation** replacing
   `LocalOnlyExportTransport`, plus a real `HisIntegrationBoundary`.

None of these can be satisfied without authorized credentials and a registered
facility, so **no transmission code was added and no connectivity is claimed**.
The honest local-only boundary remains. The admin console now states the
requirements explicitly next to the "Local only" status, and the landing page
keeps its local-identity disclosure.

## 5. Verification

- Focused: `PublicPagesIntegrationTests` 12/12, `Rd2PresentationContractTests`
  6/6, `OcrProviderTests` 5/5, `BhashiniOcrProviderTests` 6/6,
  `CapabilityStatusIntegrationTests` 5/5 (updated for the second engine),
  `PhysicianTimelineIntegrationTests`, `M5PatientKioskJourneyTests` — combined
  run **57/57**; admin/public batch **28/28**.
- Full clean regression: **787 tests, 0 failures, 0 errors, 0 skipped**
  (92 test classes), `mvn clean test` exit 0.
- Browser E2E: **not performed** — no browser automation available in this
  environment; verification is MockMvc + static contracts, labeled honestly.
- PostgreSQL-specific runtime: not exercised this session (H2 test profile);
  no schema changes were made in RD3, so migration risk is nil.
