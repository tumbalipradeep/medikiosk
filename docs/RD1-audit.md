# RD1 Audit — A Professional Healthcare Website

**Milestone:** M8 / RD1 · **Repository:** tumbalipradeep/medikiosk · **Branch:** master

RD1 (Release Design 1) turns the kiosk into a coherent, professional,
public-facing healthcare **website** without treating the repository as
greenfield. The existing backend, security model, clinical data model, tests,
provenance and honest capability boundaries are preserved; the release adds
public website pages, role-appropriate experiences, real backend integration,
accessibility and a preference system — not dashboards-for-dashboards' sake.

Guiding rules (from `checking.txt`): **no greenfield rewrite, no fake frontend
disconnected from the backend, no fabricated capability, no history rewrite,
keep backend security enforcement intact, keep scope bounded.**

This document records what was delivered, what is deliberately *not* claimed,
and how each claim is verified.

---

## 1. Baseline

- Commit before RD1: `0c3c3d5` ("Complete RW3 document intelligence and
  hardening").
- Full clean regression at baseline: 85 test classes / 718 tests, zero failures.
- `HomeController` served only `/`; there were no public informational pages.
- `SecurityConfig` had no `permitAll` routes beyond `/`, `/login`, `/register`,
  static assets and capability endpoints; admin home showed metrics, accounts,
  and conversational-AI provider status but no capability/integration summary
  and no audit surface.
- No theme / motion / text-size preferences existed; navigation was a fixed bar
  with no responsive behavior, skip link or accessibility foundation.

## 2. What was delivered

### 2.1 Public website — real pages, real controllers
- New normal pages served by `HomeController` with `permitAll` routing
  (`HomeController#home/about/features/privacy/contact`):
  - **Home** — branded landing page: "Patient Case-Taking Platform" hero, the
    canonical 5-step journey, audience lanes (patients / physicians /
    administrators), trust & safety values, and a public capability-status
    section. Every "Live" claim maps to a real implemented endpoint; OCR / HWR
    are honestly `NOT_IMPLEMENTED`; external transmission is labelled local-only.
    The demo-mode readouts the app-status JS and the regression test rely on are
    preserved: `MediKiosk`, `Patient Case-Taking Platform`, `Local demo
    deployment`, `Application Status:`.
  - **About** — the SIH26047 problem statement, engineering principles, and an
    explicit "not an autonomous diagnostician" statement.
  - **Features** — "Implemented today" list with links to each feature vs. an
    honest "not implemented" note (OCR / HWR) and the local-only boundary.
  - **Privacy** — actual data handling: local kiosk identity (no ABHA), consent
    gates, role-gated access, `Referrer-Policy: no-referrer`, no external ABDM /
    HIS transmission, browser-local preferences.
  - **Contact** — renders the operator-configured support address read live from
    the `kiosk.support_email` system setting (seeded
    `support@medikiosk.local`) with a fallback when absent.
- Shared `fragments/publicnav.html` (responsive top nav + skip link) and
  `fragments/footer.html` (`sitefooter`) reused by every public page.

### 2.2 Admin control center — capability & integration summary + audit
- `AdminConsoleService` now depends on `AuditEventRepository`,
  `OcrCapabilityService`, `HisIntegrationBoundary` and `FhirExportTransport` and
  exposes two data-derived read-only views:
  - `capabilitySummary()` — OCR overall status / engine count / provider, HWR
    status / provider, bound conversational-AI providers, HIS/ABDM boundary
    configured state + transport label, FHIR transport bean name, supported
    patient languages. Nothing is hard-coded; every value flows from the bean
    that owns the boundary (`LocalOnlyHisIntegrationBoundary`,
    `LocalOnlyExportTransport`, `OcrCapabilityService`).
  - `recentAuditEvents(int)` — the ten most recent persisted `AuditEvent` rows
    (when / event type / actor / operation / resource / outcome / case).
- `AdminConsoleController#home` passes both to `admin/home.html`, which renders
  them as "Capability & integration status" and "Recent audit events" cards with
  empty-state and honest notes. Existing metrics and the conversational-AI
  provider readers are kept.

### 2.3 Accessible, responsive role experiences
- `fragments/nav.html` (`authnav`) rewritten as a responsive `.mk-topnav` bar:
  brand, role-based links, "Signed in as {displayName}", sign-out form, and a
  skip-to-content link.
- `main` elements carry `id="main-content"` on patient home, physician
  dashboard and admin home so skip links land correctly.
- CSS foundation (`app.css`) adds: visible focus ring, `.skip-link`, responsive
  `.mk-topnav`, accessible-style comments and dark-mode / reduced-motion
  overrides for the brand, hero, footer, chat, intake and journey surfaces.
- Patient home explains "What happens next?" for the workflow.

### 2.4 Browser-local preference system
- Inline pre-paint snippet in `fragments/head.html` (and the standalone account
  page) applies stored preferences to `<html>` before first paint, avoiding a
  theme flash; `app.js` re-applies them for standalone pages.
- `static/js/prefs.js` owns the `medikiosk.prefs` localStorage key and the
  settings UI wiring: theme (system/light/dark), motion
  (dynamic/standard/reduced, defaulting to reduced when the OS requests it), and
  text size (standard/large/extra-large). Preferences stay on the browser; the
  server never sees them.
- `account/profile.html` gains a "Preferences" card with the three controls and
  honest "never sent to the server" copy; the existing profile card is untouched.

## 3. Honest boundaries (deliberately NOT claimed)

- **No OCR engine / no handwriting recognition**: public pages and the admin
  summary both report `NOT_IMPLEMENTED`; verified against `/api/capabilities/ocr`
  and `/api/capabilities/hwr`.
- **No simulated external transmission**: HIS/ABDM and FHIR export remain
  local-only no-ops with a documented boundary; the admin summary shows
  "Local-only (no external transmission)" and the `LocalOnlyExportTransport`
  bean name.
- **No fake clinic branding, patients, or generated marketing data**: demo
  mode is called out explicitly on the public pages.
- **No preference data leaves the browser**: local storage only, with no server
  endpoint.
- **Test-string contracts preserved**: landing page and sign-in strings required
  by `MediKioskApplicationTests` and `SecurityIntegrationTests` still render.

## 4. Verification

### 4.1 Test suite (full clean regression — PASS)
```
mvn -q clean test
```
Result: **86 test classes / 726 tests, 0 failures, 0 errors.**

New/updated coverage:
- `PublicPagesIntegrationTests` (7 tests) — public `/`, `/about`, `/features`,
  `/privacy`, `/contact`, honesty markers, rendered support email, availability
  for authenticated users, and redirect-off-landing for authenticated users.
- `AdminConsoleIntegrationTests` — `adminHomeRendersCapabilityStatusAndAuditSections`.

### 4.2 Bounded HTTP verification (PASS)
Fresh boot of `spring-boot:run` on an in-memory PostgreSQL-compatible H2 database
(isolated on port 8097), then read-only probes:

| Probe | Result |
|-------|--------|
| `GET /` | 200 — landing page renders |
| `GET /about`, `/features`, `/privacy`, `/contact` | 200 — public informational pages render |
| `GET /login` | 200 — rendered sign-in page |
| `GET /api/capabilities/ocr`, `/api/capabilities/hwr` | 200 — honest capability status |
| `GET /patient/cases` (anonymous) | 302 — redirect to login (security intact) |
| `GET /admin/home` (anonymous) | 302 — redirect to login (security intact) |

The verification instance was stopped afterwards.

## 5. Files changed (RD1)

New (main):
- `static/js/prefs.js`
- `templates/about.html`, `features.html`, `privacy.html`, `contact.html`
- `templates/fragments/publicnav.html`, `templates/fragments/footer.html`

Modified (main):
- `home/HomeController.java`, `config/SecurityConfig.java`
- `module/admin/service/AdminConsoleService.java`,
  `module/admin/controller/AdminConsoleController.java`,
  `module/audit/repository/AuditEventRepository.java`
- `templates/home.html`, `templates/admin/home.html`,
  `templates/account/profile.html`, `templates/patient/home.html`,
  `templates/physician/dashboard.html`, `templates/fragments/nav.html`,
  `templates/fragments/head.html`
- `static/css/app.css`, `static/js/app.js`

New/updated (tests):
- `PublicPagesIntegrationTests.java`
- `AdminConsoleIntegrationTests.java`

## 6. Known gaps (unchanged, tracked)

- Real OCR / HWR require external engines + credential verification; the seams
  and capabilities report are ready and honest but the engines are not shipped.
- Browser-level E2E automation was not available in this environment; HTTP
  verification is via the MockMvc suite plus bounded live probes.
- Preferences are browser-local and do not roam; a server-side per-account
  preference store is a candidate for a later release (RD2/RD3 track).