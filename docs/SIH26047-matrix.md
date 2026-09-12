# SIH26047 Theme Matrix — M5.3 (Final Hardening & Delivery)

Each row records a SIH26047 theme relevant to the patient kiosk journey and how
the final M5.3 build addresses it in this codebase. Where a theme is only
partially covered or deliberately out of scope, the reason is stated plainly.
No claim is made for capabilities this project does not implement
(ABHA/ABDM connectivity, live national LLM APIs, telemedicine referral
networks, etc.). This is an honest review for the Hackathon checkpoint, not an
overclaim.

Legend: PASS = shipped and verified in M5.3 · PARTIAL = addressed to a defined
degree with the remainder explicitly out of M5.3 scope · NOT IN SCOPE = not
part of this build and no claim is made.

| # | SIH26047 theme (recurring themes across the theme list) | Verdict | What this build does (final M5.3) |
|---|----------------------------------------------------------|---------|------------------------------|
| 1 | Accessible, patient-friendly kiosk UI for underserved users | PASS | Journey stepper, large touch targets (`btn-lg`/`btn-lg-mobile`), progress text, `aria-live` status region, logical headings, clear plain-language copy. `identify`/`consent`/`intake`/`complete` rewritten in earlier milestones and retained. |
| 2 | Multilingual patient interaction (Bharat languages) | PASS | Offline curated translations of all 25 deterministic clinical questions for Hindi, Telugu, Tamil, Kannada (in addition to English); language selector with native scripts; stored with its true `QuestionSource.TRANSLATED` and BCP-47 tag; the physician workspace surfaces the exact language and wording per answer. |
| 3 | Speech input/output modalities (voice, text LUIS-style) | PASS | Server-side ASR (audio upload → transcript) and TTS (question read aloud) with honest availability feedback; clean text fallback on every screen. Answer source (voice vs typed) recorded and shown as provenance per answer. |
| 4 | ABHA / Ayushman Bharat Digital Mission (ABDM) integration | PARTIAL | M5.2 deliberately builds the *identity boundary against* ABHA: the kiosk declares a `LOCAL`, non-ABHA, non-Aadhaar identity and states plainly that nothing is sent to ABDM. Real ABHA linkage requires ABDM credentials/consent machinery and is explicitly out of M5.2 scope (no fabricated ABHA numbers anywhere). |
| 5 | Consent-first privacy / data governance | PARTIAL→PASS | Granular per-purpose consent (clinical case-taking, audio capture, document processing, data sharing) with grant/revoke, and `PatientSessionService.start` refuses to open a session without clinical-case-taking consent. The physician workspace adds a Consent Boundary panel that explicitly distinguishes granted kiosk consent from any external transmission, which is not configured. |
| 6 | AYUSH-specific clinical workflows (Dashavidha Pariksha, Ahara-Vihara) | PASS | 10-parameter Dashavidha + 8-parameter Ahara-Vihara planners are the second and third phases of the intake; the physician workspace groups answers under Dashavidha Pariksha and Ahara-Vihara with the exact wording shown to the patient at answer time. |
| 7 | Deterministic, auditable offline reasoning instead of unvalidated AI | PASS | Question sequence, red-flag evaluation, and completion are fully deterministic and run offline and re-derive red flags deterministically from the persisted verbatim answers on every workspace load; AI is only a wording layer (English-only) with validated fallback; the exact text shown, its source, and language are stored per answer. |
| 8 | Patient safety: urgent-sign triage | PARTIAL | Deterministic red-flag rules raise urgency and are surfaced on the physician workspace with a URGENT severity banner and "Clinical review required; this is not a diagnosis" wording. The honest limitation: rule text-matching is English-keyword based. |
| 9 | Chronic/community health data capture and continuity | PARTIAL | Answers and identified documents persist as a completed case; the physician workspace turns each case into a durable, reviewable artefact with persistent accept/amend/reject decisions and provenance. No longitudinal analytics/LMR link in M5.2 scope. |
| 10 | Document handling (upload intake forms/reports) | PASS | Patient attaches clinical documents (PDF/JPEG/PNG, max 10 MB); the physician workspace lists attachments with honest extraction state, structured findings (extraction failure categories such as ENCRYPTED retried by the physician) and a document timeline. `complete.html` shows an honest demo-mode notice. |
| 11 | Data security and access control | PASS | Patients can only read their own case/documents (ownership enforced with 404 otherwise); physician review/workspace/FHIR routes remain role-protected; review decisions require the physician role and CSRF; no clinical payloads in URLs/localStorage/console, and audit rows carry identifiers plus outcome only. |
| 12 | Scalability/offline resilience for low-bandwidth settings | PASS | Stateless step endpoints, session-carried conversation state, offline deterministic translations, dependency-free fallback, deterministic FHIR export (byte-for-byte repeatable, no network dependency). Verified under `mvn -o` (offline). |
| 13 | Accessibility & assistive use (visual/hearing) | PASS | `aria-live`/`role=status` regions for progress and voice state, `role=log` chat, `aria-current` on active step, keyboard-enter submission, alt-free decorative SVGs labelled `aria-hidden`, and honest no-microphone/unavailable voice messaging. |
| 14 | Transparent AI / honest boundaries (no fake diagnosis) | PASS | Workspace, flags banner, completion page, interop and consent boundary panels all say "not a diagnosis", "stored locally", "demo mode", "no external transmission", and "AI is only wording, never diagnosis". |
| 15 | Telemedicine / virtual consultation / referral | NOT IN SCOPE | Not implemented in M5.2; the kiosk hands off to the in-clinic physician review. No claim of teleconsult or referral-network coverage. |
| 16 | Disease prediction / population dashboards / analytics | NOT IN SCOPE | Not implemented; the red-flag layer is triage wording, not prediction, and the physician dashboard lists cases rather than predicting outcomes. |
| 17 | e-pharmacy / e-prescription / payments / inventory | NOT IN SCOPE | Not part of M5.2. |
| 18 | Wearables / IoT / sensor integration | NOT IN SCOPE | Not part of M5.2. |
| 19 | Unified state healthcare data / EMR interoperability (FHIR) | PARTIAL | Deterministic, repeatable FHIR R4 Bundle export per completed case (collection, stable ids, no document binaries, standard HL7 code systems only, no fabricated LOINC/SNOMED/UCUM), with a documented interoperability boundary (`HisIntegrationBoundary`) whose current implementation is a no-op — a future ABDM/HIS adapter replaces that bean. No live interop is claimed. |
| 20 | Offline-first usability in rural/low-power settings | PASS | Everything from intake to physician review and FHIR export runs offline against the local store; the offline H2 mode and Postgres mode both verified. |

## M5.3 kill-test verification (nothing regressed from the matrix above)
- Conversation resume that never wipes in-progress work; a completion page that
  refuses to render when no completed caseId is in the session (redirects home).
- Comma-decimal lab values ("8,4 mg/dL") are read as a decimal point, and
  thousands-grouped values ("Hb 14,500 /mm3" style) are disambiguated as
  thousands, never as decimals.
- The red-flags card always renders — including an honest `None detected`
  zero-state — so "not flagged" can never be confused with a skipped check.
- Review double-submits are single-row-upsert and the DB uniqueness constraint
  remains the final backstop; physician workspace, FHIR, and audit paths are
  role-gated (physician reaching `/admin/**` gets 403).
- Global `Referrer-Policy: no-referrer` response header on every page; the
  Bootstrap CDN link additionally sends `referrerpolicy="no-referrer"`.
- Anonymous `/actuator/health` returns only the aggregate status (no db/disk
  detail) thanks to `show-details: when-authorized`; only `health` and `info`
  are exposed.
- Live FHIR export is byte-for-byte repeatable with `application/fhir+json`
  and `X-Fhir-Version: 4.0.1`, and every code system resolves to a real
  HL7/UCUM/UUID/terminology namespace (no fabricated LOINC/SNOMED).

## Honest limitations recorded explicitly
- Red-flag detection matches English keywords only; translations of urgent-sign
  detection are not performed on non-English answer text.
- No real ABHA/ABDM connectivity — the kiosk operates in a clearly-labelled
  `LOCAL`/demo identity mode, the FHIR export is a local projection, no
  transmission is performed, and no ABHA or Aadhaar identifiers are fabricated.
- AI conversational rewording is English-only and config-gated; without
  provider credentials the deterministic canonical wording is used.
- DOCUMENT processing is a local demo (optional provider-gated), extraction can
  fail honestly (ENCRYPTED/UNREADABLE), and findings are staged with the
  caveat "may contain errors and must be clinically verified".
- Duplicate-word grouping in chronic conditions is off; nothing is
  presented as assumed.
- Physician review decisions are stored per answer with the original evidence
  preserved; amended wording is physician-entered and never overwrites what the
  patient said.
- A/Q every claim above is exercised by the live browser-level kill test
  in `docs/M5.3-final-hardening-and-kill-test.md`.

Generated for the SIH26047 hackathon checkpoint (M5.3), Trento-style honest
self-assessment. See `docs/M5.2-physician-interoperability-clinical-trust-audit.md`.