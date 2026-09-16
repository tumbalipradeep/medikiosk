# RD2 Audit — Product deepening and interaction hardening

Scope of this document: everything actually implemented and verified in the
RD2 working tree on top of the RD1 checkpoint (`0f2889d`). Facts only; no
claimed integrations.

## 1. What RD2 added

| Area | Status | Where |
|---|---|---|
| P0 truthfulness repairs (landing status, health URL, derived AI badge) | Complete, verified | `app.js`, `home.html`, `HomeController`, `AiFailoverService`, `CapabilityStatusController` |
| Password policy — minimum 6 characters, no character-class requirements | Complete, verified | `SecurityPolicyProperties`, `application.yml`, `register.html`, `password.html`, `PasswordPolicy` |
| Server-side display preferences (theme/motion/text size) | Complete, verified | `V25__user_display_preferences.sql`, `module/auth/preferences/`, `fragments/prepaint.html` |
| Source-language provenance surfaced in physician document UI | Complete, verified (null-safe; pipeline records null today) | `DocumentWorkspaceItem`, `DocumentDetailResponse`, `PhysicianTimelineService`, `physician/case.html` |
| Truthful voice capability endpoint | Complete, verified | `GET /api/capabilities/voice`, admin voice row |
| Physical/spatial motion layer | Complete, verified statically | `app.css` RD2 motion section, `physician.js` dialog origin |
| Failure-first feedback (`MkFeedback`/`MkFetch`) + session-expiry bar | Complete, verified | `static/js/mk-errors.js`, wired into intake/physician/adaptive-history/record |
| `alert()` removal from professional workflows | Complete, verified | physician.js 12→0, adaptive-history.js 2→0 |
| Patient correction loop (additive evidence) | Complete, verified | `V26__patient_answer_corrections.sql`, `module/patient/correction/`, patient review UI, physician workspace |
| Admin audit trail page (filters + pagination) | Complete, verified | `/admin/audit`, `AuditQueryService`, `admin/audit.html` |
| Settings-change auditing | Complete, verified | `SETTINGS_CHANGE` audit events in `AdminConsoleService` |
| Mobile intake polish | Complete, verified statically | `app.css` mobile intake rules |

## 2. Password policy

The only mandatory rule is **length ≥ 6**. No uppercase/lowercase/digit/special
requirement. Applied consistently in `PasswordPolicy` (server-side), property
defaults, `application.yml`, and both templates (`minlength="6"` + help text).
Registration, password change, and physician temporary-password flows all
route through `PasswordPolicy`. Hashing (BCrypt), lockout, session fixation
protection, CSRF, and role boundaries are unchanged.

Regression tests: `PasswordPolicyIntegrationTests` (10) — 6-char accepted,
5-char rejected, letters-only accepted, digits-only accepted, no-special
accepted, authentication unchanged, no role escalation.

## 3. Server-side display preferences (V25)

`user_display_preferences` shares the `users` primary key (`@MapsId` FK),
with CHECK constraints on the closed value sets:

- theme: `system` (default) / `light` / `dark`
- motion: `dynamic` / `standard` / `reduced`
- text size: `standard` (default) / `large` / `xlarge`

Behavior:

- Authenticated users persist via CSRF-protected `POST /account/preferences`
  (owner is always the authenticated principal — cross-user access is
  structurally impossible). Anonymous users remain browser-local
  (`localStorage`); no cookies or fingerprinting were added.
- When a stored row exists, the server seed drives the pre-paint theme
  (removing the flash-of-wrong-theme for signed-in users on a fresh browser);
  otherwise `localStorage` stays authoritative.
- The pre-paint snippet is single-source: `fragments/prepaint.html`, consumed
  by `fragments/head.html` (all 22 head-fragment pages) and
  `account/profile.html`.
- Physician profile `theme` continues to work (existing tests untouched) and
  now propagates into display preferences so there is one theme truth.

Tests: `DisplayPreferencesIntegrationTests` (9) — defaults, persistence+seed,
invalid-value rejection without persistence, per-user isolation, CSRF denial,
anonymous redirect, admin save, physician theme propagation.

## 4. Provenance and capability truth

- `source_language` is carried in `DocumentWorkspaceItem` and
  `DocumentDetailResponse` and rendered in the physician workspace as
  "Source language". The current extraction pipeline records `null` (no
  language detection is implemented), so older and current records surface
  nothing rather than an invented value. When a genuine provider records a
  language, it flows through unmodified.
- `GET /api/capabilities/voice` reports voice provider configuration state
  using the same status discipline as OCR/HWR. It exposes only provider
  names and enabled booleans — never keys, headers, or patient data.
- The admin control-center capability summary gained a truthful voice row.
- `UNKNOWN` remains distinct from `NORMAL`; lab values without a reference
  range are still `UNKNOWN` (unchanged RW3 discipline).

Tests: `CapabilityStatusIntegrationTests` voice cases (no-credentials,
enabled/disabled derived from `VoiceProperties`), source-language truthful-null
assertions in `PhysicianTimelineIntegrationTests`.

## 5. Physical/spatial motion

Extended the existing token architecture (`--mk-motion-*`) with:

- button press compression + spring release (`--mk-motion-press-scale`,
  spring-release easing)
- restrained card lift for `.mk-liftable` cards
- origin-aware dialog entrance (`mk-dialog-in`, `--mk-dialog-origin`) using
  the existing `mk-lamp-in` keyframes; wired into the physician case modals
  with a click-point transform-origin helper in `physician.js`
- intake chat bubble entrance (`mk-bubble-in`)
- spatial navigation indicator movement
- reduced-motion double guard (`prefers-reduced-motion: reduce` media query
  AND `html[data-mk-motion="reduced"]`) for every new surface

Constraints verified by `Rd2PresentationContractTests` (6): no
`transition: all` anywhere in the stylesheet, keyframe animation surfaces
disabled in reduced-motion, and all animations confined to
transform/opacity. The Linux/compositor reference informed interaction
feel only; the visual identity remains MediKiosk's own.

## 6. Failure-first UX

New shared module `static/js/mk-errors.js`:

- `MkFetch` — drop-in `fetch` wrapper with typed rejections
  (`kind: 'session'` for 401/403, `kind: 'network'` for connectivity loss)
- `MkFeedback` — `aria-live` inline feedback with honest messages and a
  prominent session-expiry bar offering the sign-in recovery path

Applied to intake.js, physician.js, adaptive-history.js, record.js. All 12
`alert()` calls in physician.js and 2 in adaptive-history.js were replaced
with inline, accessible feedback (verified by static test). Session expiry
mid-intake now shows the recovery bar instead of failing silently; no answer
text is claimed to be preserved across sessions (no durable PHI storage was
added).

## 7. Patient correction loop (V26)

`patient_answer_corrections` is **additive clinical evidence**:

- keyed `(case_id, answer_order)`, mirroring `physician_review_entries`
- stores an immutable server-side snapshot of the original answer; the
  persisted original row is never modified
- the snapshot is re-taken from the database on every submission, so a
  correction can never attach to different evidence than the patient saw
- one current correction per answer; resubmission replaces the patient's
  pending correction (delete flushed before insert to respect the unique
  constraint)
- every accepted correction writes a `PATIENT_CORRECTION` audit event
- ownership: a patient can only correct answers of their OWN case; foreign
  cases return 404 (existence not revealed); mounted under `/patient/**`
  (role + CSRF inherited)
- a correction equal to the original, empty, or oversized is rejected
- physician workspace shows original and correction side by side with actor,
  timestamp and reason, explicitly marked "Awaiting physician review"; the
  clinical decision remains the distinct `PHYSICIAN_REVIEW` flow
- if a correction's snapshot no longer matches the persisted answer, the
  workspace hides the correction rather than presenting stale data
- the patient review panel offers a per-answer "This isn't right" action with
  an accessible confirm dialog (original shown, `aria-modal`, focus restore,
  Escape/cancel) and a "Corrected by you" badge after submission

Tests: `PatientCorrectionIntegrationTests` (13) — happy path, summary
surfacing, resubmission, cross-patient 404 (read + write), anonymous 302,
CSRF 403, physician role 403, empty/equal/unknown-order rejection, audit
event visible in `/admin/home`, physician-side distinct rendering.

## 8. Admin control center (Phase 6)

- New `/admin/audit` page: filter by event type, actor (case-insensitive
  contains), outcome, and case id; newest-first with clamped page sizes
  (10/25/50/100), enforced `ROLE_ADMIN` via method security.
- `SETTINGS_CHANGE` audit events now record every runtime setting
  update/delete with actor and setting key (the value is deliberately never
  logged — it can be operationally sensitive).
- Admin remains a platform-control role: no clinical-history editing surface
  was added.

Tests: `AdminConsoleIntegrationTests` (+4) — audit page renders with filters,
non-admin 403, case filter isolation, settings update and deletion audited
with actor, key, and no value leakage.

## 9. Database changes

Exactly two additive migrations; V1–V24 untouched:

- `V25__user_display_preferences.sql`
- `V26__patient_answer_corrections.sql`

Both are PostgreSQL/H2 compatible and validated under `ddl-auto: validate`.

## 10. Security review

- New endpoints: `/account/preferences` (authenticated, self only),
  `/patient/cases/{id}/corrections` (PATIENT role + CSRF + ownership),
  `/api/capabilities/voice` (anonymous read of configuration state only — no
  secrets, no patient data), `/admin/audit` (ADMIN via method security).
- Horizontal access: enforced in services (ownership filters) and covered by
  tests (cross-patient correction denied 404; per-user preference isolation).
- CSRF remains globally on; new POST endpoints are CSRF-protected and tested.
- No authorization weakening accompanied the simpler password rule.

## 11. Verification

- Full clean regression: **780 tests, 0 failures, 0 errors, 0 skipped**
  (91 test classes executed, including the 6 `Rd2PresentationContractTests`
  static contracts and focused `PatientCorrectionServiceTests` for the
  409 race mapping and single-state correction lifecycle).
- Focused suites per phase: password 10/10, display preferences 9/9,
  capability/voice 16/16 (Phase 2 batch), corrections 13/13, admin 16/16,
  presentation contracts 6/6.
- `git diff --check` clean; no secrets, logs, temp files, or debug code
  introduced; build logs kept out of the repository.
- Browser E2E: **not available in this environment** (no browser automation
  installed, per directive). Verification used MockMvc integration tests and
  static source contracts, labeled honestly. No browser verification is
  claimed.

## 12. External API feasibility outcome

The public-apis catalogue was treated as a catalogue only. No candidate
provides a genuinely suitable, privacy-safe clinical capability (OCR of
medical documents, HWR, ASR/TTS for Indian languages, clinical reasoning)
that MediKiosk could integrate without sending patient data to third
parties. **No external API was integrated.** OCR/HWR remain honestly
`NOT_IMPLEMENTED`; voice and AI statuses derive from real configuration;
the existing local deterministic fallbacks are preserved.

## 13. Limitations and deferred work

- Source language is plumbed end-to-end but is null today because no
  language-detection provider exists; nothing was fabricated to fill it.
- Patient corrections are single current-correction-per-answer; full
  correction history is not kept (the original evidence remains intact).
  A concurrent duplicate submission is rejected with a controlled 409 that
  never exposes database detail; the correction status column carries only
  `SUBMITTED` — the physician's decision lives in physician_review_entries
  (audited as `PHYSICIAN_REVIEW`), keeping the three artifacts distinct.
- Cross-session draft persistence for intake was explicitly out of scope.
- Deferred P2: further mobile intake restructuring, Chrome i18n dictionary
  deduplication, role-aware public navigation, review-decision history
  display.
- Browser E2E remains unavailable; MockMvc + static contracts are the
  verification of record.
