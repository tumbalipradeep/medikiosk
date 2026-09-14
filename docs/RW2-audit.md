# RW2 Audit — Clinical Intelligence Foundation

Rides on top of the RW1 baseline (`c8eceaf`). RW2 turns the demo/questionnaire
kiosk into a serious clinical workflow platform. All areas below are complete
and were closed together in a single milestone commit.

## Completed RW2 Areas

- **Account lifecycle** — registration, login lockout with failure handling,
  password change, session listing/termination, account status (active /
  locked / disabled).
- **Admin console** — dashboard, account management (activate / deactivate /
  lock / unlock / reset password), physician provisioning with
  qualification/department/specialty, physician profile administration, system
  settings (editable and deletable), seed data for settings.
- **Profile self-service** — patients and physicians edit their own profile
  (date of birth, gender, contact, email, addresses, emergency contact, blood
  group, preferred language, theme, notification and accessibility
  preferences). Admin accounts are read-only in the self-service profile.
  Profile pictures are stored under a dedicated dedicated storage component
  (`module/profile/storage`), ownership-checked serving, 2 MB cap, JPG/PNG/WebP.
  Physician professional lines (qualification / department / specialty) are
  **not** editable through self-service; they are administered by the admin
  console.
- **Clinical domain foundation** — encountered lifecycle plus a complete
  clinical history model (chief complaint, history of present illness, past
  medical, surgical history, medications, allergies, family, social, review of
  systems), with clinical provenance on every item.
- **Adaptive conversation planner** — a deterministic planner that completes an
  honest patient-history interview from captured answers and a question bank.
- **Red-flag triage** — system-detected flags re-derived from the patient's own
  words, plus physician-assessed triage actions (escalation notes, source of
  assessment).
- **Medication interaction screen** — normalized medication names, interaction
  rule engine with severity, surfaced to the physician.
- **Physician clinical record** — case assignment and queue, record page with
  red-flag triage, auto-seeded clinical summary drafts with amend / accept /
  reject, consultation with finalization, timeline and FHIR export.
- **Professional product UX** — branded identity (logo, favicon, apple touch
  icon), motion system with `prefers-reduced-motion` support.
- **Bug fixes** — Thymeleaf bean-expression regression in
  `physician/record.html` (malformed `@clinicalSummaryService.sectionLabel(...)`
  corrected to `${@clinicalSummaryService.sectionLabel(summary.section)}`).

## Tests

- Final complete Maven suite: **698 tests, 0 failures, 0 errors, 0 skipped.**
- New RW2 test classes include: `ProfileSelfServiceIntegrationTests` (9),
  `PhysicianClinicalRecordControllerIntegrationTests` (9),
  `AdminConsoleIntegrationTests`, `CaseAssignmentIntegrationTests`,
  `ClinicalRecordAutoSeedingIntegrationTests`, `AdaptiveConversationPlannerTests`,
  `AdaptiveHistoryConversationIntegrationTests`, `MedicationInteractionEngineTests`,
  `MedicationInteractionIntegrationTests`, plus updated `SecurityIntegrationTests`
  and `PhysicianFhirControllerFailureAuditUnitTest`.

## Bounded HTTP/HTML Verification

Real browser automation was **unavailable** in this environment. Verification
used bounded HTTP/HTML checks against the live application
(http://localhost:8081, `local` profile, fresh in-memory H2) with per-request
timeouts. Results:

| Outcome  | Count |
|----------|-------|
| PASS     | 21    |
| FAIL     | 0     |
| TIMEOUT  | 0     |
| BLOCKED  | 4     |

### Passes (21)

- Public: `/login`, `/register`, landing `/`
- Patient: login, `/patient/home`, `/account/profile`, `/account/password`,
  `/account/sessions`, `/patient/history`, `/patient/identify`,
  POST `/patient/identify/confirm`, `/patient/consent`, `/patient/intake`,
  `/patient/intake/complete`, `/patient/history/adaptive/state`,
  POST `/patient/history/adaptive/start`
- Physician: login, `/physician/home`, `/physician/review`
- Admin: login, `/admin/home`, `/admin/accounts`, `/admin/settings`
- `/account/profile/picture` returned the expected 404 (no picture uploaded)

### Blocked (4)

The physician case workspace left the fresh in-memory H2 database empty of
seeded cases, so these pages had nothing to render against and were not
fabricated:

- `/physician/cases/{caseId}` (workspace)
- `/physician/cases/{caseId}/reviews`
- `/physician/cases/{caseId}/record`
- `/physician/cases/{caseId}/timeline`

These flows are covered by MockMvc integration tests
(`PhysicianClinicalRecordControllerIntegrationTests`,
`CaseAssignmentIntegrationTests`, and the physician workspace/FHIR suites).

### Server / Template Errors

- 0 exceptions in the server error log during verification
- 0 exceptions in the application log
- No Thymeleaf rendering errors detected on any verified page

## Demo Seed Accounts

- Patient: `patient` / `patient123`
- Physician: `physician` / `physician123`
- Admin: `admin` / `admin123`