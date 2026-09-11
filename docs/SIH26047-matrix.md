# SIH26047 Theme Matrix — M5.1 (Patient Kiosk Experience)

Each row records a SIH26047 theme relevant to the patient kiosk journey and how
M5.1 addresses it in this codebase. Where a theme is only partially covered or
deliberately out of M5.1 scope, the reason is stated plainly. No claim is made
for capabilities this project does not implement (ABHA/ABDM connectivity, live
national LLM APIs, telemedicine referral networks, etc.). This is an honest
review for the Hackathon checkpoint, not an overclaim.

Legend: PASS = shipped and verified in M5.1 · PARTIAL = addressed to a defined
degree with the remainder explicitly out of M5.1 scope · NOT IN SCOPE = not
part of M5.1 and no claim is made.

| # | SIH26047 theme (recurring themes across the theme list) | Verdict | What this build does in M5.1 |
|---|----------------------------------------------------------|---------|------------------------------|
| 1 | Accessible, patient-friendly kiosk UI for underserved users | PASS | Journey stepper, large touch targets (`btn-lg`/`btn-lg-mobile`), progress text, `aria-live` status region, logical headings, clear plain-language copy. `identify`/`consent`/`intake`/`complete` rewritten for M5.1. |
| 2 | Multilingual patient interaction (Bharat languages) | PASS | Offline curated translations of all 25 deterministic clinical questions for Hindi, Telugu, Tamil, Kannada (in addition to English); language selector with native scripts; conversation re-localizes on the fly; served text is recorded with its true `QuestionSource.TRANSLATED` and BCP-47 tag. |
| 3 | Speech input/output modalities (voice, text LUIS-style) | PASS | Server-side ASR (audio upload → transcript) and TTS (question read aloud) with honest availability feedback; clean text fallback on every screen. No AI provider credentials shipped → deterministic fallback verified. |
| 4 | ABHA / Ayushman Bharat Digital Mission (ABDM) integration | PARTIAL | M5.1 deliberately builds the *identity boundary against* ABHA: the kiosk declares a `LOCAL`, non-ABHA, non-Aadhaar identity and states plainly that nothing is sent to ABDM. Real ABHA linkage requires ABDM credentials/consent machinery and is explicitly out of M5.1 scope (no fabricated ABHA numbers anywhere). |
| 5 | Consent-first privacy / data governance | PASS | Granular per-purpose consent (clinical case-taking, audio capture, document processing, data sharing — expanded to all four on the consent page) with grant/revoke, and `PatientSessionService.start` refuses to open a session without clinical-case-taking consent. |
| 6 | AYUSH-specific clinical workflows (Dashavidha Pariksha, Ahara-Vihara) | PASS | 10-parameter Dashavidha + 8-parameter Ahara-Vihara planners are the second and third phases of the intake; the physician review groups answers by Dashavidha Pariksha and Ahara-Vihara; progress stepper names the AYUSH phases. |
| 7 | Deterministic, auditable offline reasoning instead of unvalidated AI | PASS | Question sequence, red-flag evaluation, and completion are fully deterministic and run offline; AI is only a wording layer (English-only) with validated fallback; the exact text shown and its source are stored per clinical answer. |
| 8 | Patient safety: urgent-sign triage | PARTIAL | English-keyword deterministic red-flag rules raise urgency, are surfaced with `role="alert"` (`redFlagTitle`) and on the review/complete pages with an "seek urgent care now" message. The honest limitation: rule text-matching is English-keyword based (authentic for an offline hackathon build, but a full system needs NLU + medical validation). |
| 9 | Chronic/community health data capture and continuity | PARTIAL | Answers and identified documents are persisted as a completed case retrievable by the physician and shown to the patient for review; repeat visits are supported via completed-session lifecycle. No longitudinal analytics/LMR link in M5.1 scope. |
| 10 | Document handling (upload intake forms/reports) | PASS | Patient can attach clinical documents (PDF/JPEG/PNG, max 10 MB), list, remove, and the physician review shows attachments. `complete.html` shows an honest demo-mode notice. |
| 11 | Data security and access control | PASS | Patients can only read their own case/documents (ownership enforced with 404 otherwise); physician-only review routes remain protected by role; session-scoped conversation staging; no clinical data in URLs/localStorage/console beyond a non-clinical case id in a `?caseId=` review link (that page re-verifies ownership server-side). |
| 12 | Scalability/offline resilience for low-bandwidth settings | PASS | Stateless step endpoints, session-carried conversation state, offline deterministic translations, and dependency-free fallback behavior; verified under `mvn -o` (offline). |
| 13 | Accessibility & assistive use (visual/hearing) | PASS | `aria-live`/`role=status` regions for progress and voice state, `role=log` chat, `aria-current` on active step, keyboard-enter submission, alt-free decorative SVGs labelled `aria-hidden`, and honest no-microphone/unavailable voice messaging. |
| 14 | Transparent AI / honest boundaries (no fake diagnosis) | PASS | Review panel, completion page, and copy consistently say "not a diagnosis", "stored locally", "demo mode", "nothing sent to ABDM", "AI is only wording, never diagnosis". |
| 15 | Telemedicine / virtual consultation / referral | NOT IN SCOPE | Not implemented in M5.1; the kiosk hands off to the in-clinic physician review. No claim of teleconsult or referral-network coverage. |
| 16 | Disease prediction / population dashboards / analytics | NOT IN SCOPE | Not implemented in M5.1; the red-flag layer is triage wording, not prediction. |
| 17 | e-pharmacy / e-prescription / payments / inventory | NOT IN SCOPE | Not part of M5.1. |
| 18 | Wearables / IoT / sensor integration | NOT IN SCOPE | Not part of M5.1. |
| 19 | Unified state healthcare data / EMR interoperability (FHIR) | PARTIAL | Existing FHIR mapping (M4) remains available for future ABDM/EMR export; M5.1 keeps the patient record in the kiosk's own store and does not claim live interop. |
| 20 | Offline-first usability in rural/low-power settings | PASS | Everything in the patient journey except optional AI wording runs offline; the offline H2 mode and Postgres mode both verified. |

## Honest limitations recorded explicitly
- Red-flag detection matches English keywords only; translations of urgent-sign
  detection are not performed on non-English answer text in M5.1.
- No real ABHA/ABDM connectivity — the kiosk operates in a clearly-labelled
  `LOCAL`/demo identity mode and never fabricates ABHA or Aadhaar identifiers.
- AI conversational rewording is English-only and config-gated; without
  provider credentials the deterministic canonical wording is used.
- Completion page / summary endpoint show counts and red-flag status honestly;
  they are explicitly described as never being a diagnosis.

Generated for the SIH26047 hackathon checkpoint (M5.1), Trento-style honest
self-assessment.