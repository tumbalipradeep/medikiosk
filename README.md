# MediKiosk

**Patient Case-Taking Platform** — SIH26047

A modular-monolith web application for taking patient cases at a medical kiosk.

## Technology Stack

- Java 25
- Spring Boot 4.1.1
- Maven
- PostgreSQL 17+
- Thymeleaf
- Flyway
- Spring Security
- Bootstrap
- Vanilla JavaScript
- Modular monolith architecture

## Milestone Status

**M1 — Clinical document intelligence (complete).** Authentication/security
(role-based patient, physician and admin access with CSRF and login), patient
clinical intake, AYUSH Dashavidha Pariksha and Ahara-Vihara questions, red
flags, structured clinical summary, and clinical document
upload/extraction/findings/timeline.

**M2 — Adaptive AI clinical conversation (complete).** A deterministic clinical
dialogue driven by a structured question plan, with an adaptive AI layer
(environment-provided Groq / Gemini / OpenRouter adapters) that only re-words,
never invents, the next question. Physician review and persistent completed
cases complete the case-taking loop.

**M3 — Multilingual voice-enabled intake (complete).** Multilingual intake
(English, Hindi, Telugu, Tamil, Kannada) with voice ASR/TTS and a Bhashini
adapter. Live speech requires environment-provided credentials; without them a
deterministic `UNAVAILABLE` fallback keeps the patient on typed input.

**M4 — Admin management (complete).** Admin home with user and system state
overview.

**M5.1 — Physician clinical trust (complete).** Deterministic repeatable FHIR
R4 export for completed cases (audit trail, ABDM-ready boundary, byte-for-byte
repeatability), physician timeline and provenance view, structured findings
from documents, and honest demoscape boundaries (no fabricated identifiers or
external claims).

**M5.2 — Physician + interoperability + clinical trust (complete).** A
physician dashboard with per-case workspaces that persist accept / amend /
reject review decisions next to the original captured evidence, surface
provenance (question source, answer source, language, wording), re-derive red
flags deterministically from the patient's own words on every load, show
document extraction / findings / timeline inline, and expose a clear
interoperability and consent boundary with no simulated ABDM or HIS
transmission (any real adapter is a no-op until configured and audited).
Every review decision is audited; unexpected FHIR export failures are audited
and surfaced as 500s. See `docs/M5.2-physician-interoperability-clinical-trust-audit.md`.

**M5.3 — Final hardening & kill test (complete).** Adversarial end-to-end kill
test of every phase with the honest-demo hygiene rules enforced: a half-answer
search-and-resume (45 seconds of case-taking lost zero), a completion endpoint
that rendering with a stale or tampered session, interquartile comma-decimal
lab normalization that reads "8,4 mg/dL" as a decimal point and never as a
thousands group, a red-flags card that always renders with an honest `None
detected` zero-state, review double-submits that cannot create a second row,
CSRF-secured conversation/voice/review endpoints with the global
`Referrer-Policy: no-referrer` header, `show-details: when-authorized` health
meters and a no-detail health aggregate for anonymous browsers, and a live FHIR
bundle that is byte-for-byte repeatable with no fabricated code systems. Zero
end-to-end defects were open; the milestone closes only after the full clean
regression and a final browser-level re-run.

**M6 — RW2: Clinical intelligence foundation (complete).** The demo kiosk
matures into a serious clinical workflow platform. Account lifecycle and
self-service (registration, login lockout and failure handling, password
change, session control, admin account management, patient and physician
profile self-service with profile pictures), a complete clinical domain
(encounters, complete clinical history across chief complaint / HPI / past
medical / surgical / medications / allergies / family / social / review of
systems, AYUSH, Ahara-Vihara), an adaptive conversation planner that completes
an honest history interview, deterministic red-flag triage with physician
assessment, a medication interaction screen, clinical provenance on every
answer, a physician clinical-record workspace (case assignment and queue,
red-flag triage, auto-seeded clinical summaries with amend / accept / reject,
consultation with finalization), professional product UX (branded identity,
motion system with reduced-motion support), and a 698-test suite with zero
failures. See `docs/RW2-audit.md`. Bounded HTTP/HTML verification: 21 PASS /
0 FAIL / 0 TIMEOUT / 4 BLOCKED (physician case pages have no seeded data in a
fresh in-memory database); real browser automation was unavailable in this
environment.

**M7 — RW3: Document intelligence & hardening (complete).** Document
uploads are now content-validated by magic bytes (PDF / JPEG / PNG), so a
MIME-spoofed file is rejected even when its extension and content type agree. A
new OCR capability architecture declares the honest OCR boundary instead of a
placeholder: `module/ocr` reports real provider status
(`NOT_IMPLEMENTED` in this deployment), evaluated languages (English / Hindi /
Telugu) and a deterministic fallback selection, and refuses to relabel an
image as extractable. A handwriting-recognition seam exists but is honestly
`NOT_IMPLEMENTED` — ordinary OCR is never presented as HWR. Nothing is
fabricated: extraction provenance (method PDF_TEXT / OCR_IMAGE / NONE and
provider) is persisted and surfaced in the physician workspace and document
details; the clinical timeline now includes extraction-completed,
abnormal-lab-detected, physician-review, encounter-submitted and
consultation-finalized events derived only from persisted data; lab values
without a printed reference range are always `UNKNOWN`, never judged abnormal.
Anonymous `/api/capabilities/ocr` and `/api/capabilities/hwr` endpoints expose
the honest status. See `docs/RW3-audit.md`. Full clean regression:
85 test classes / 718 tests, zero failures; bounded HTTP/HTML verification
PASS on a freshly booted in-memory instance.

**M8 — RW1: A professional healthcare website (complete).** RD1 turns the
kiosk into a coherent, public-facing healthcare website that stays fully
wired to the real backend. A normal public site (Home / About / Features /
Privacy / Contact) sits alongside the role portals and is served by real
controllers with `permitAll` routing; the landing page keeps the demo-mode
application status live readout and publishes the same honest capability
status as the API. Role experiences are distinct: patients get clarity on the
intake journey, physicians a clinical workspace, administrators a control
center that shows a data-derived capability & integration summary (OCR / HWR /
conversational AI providers / external-transmission boundary / FHIR transport)
and the ten most recent persisted audit events. A documented accessibility
foundation is applied everywhere: visible focus, skip links, keyboard
navigation, a `medikiosk.prefs` browser-local preference layer for theme
(system/light/dark), motion (dynamic/standard/reduced with an OS
reduced-motion default) and text size (standard/large/extra-large), plus a
responsive top navigation. Nothing is fabricated: every "Live" claim on the
public site maps to a real implemented endpoint, OCR/HWR remain
`NOT_IMPLEMENTED`, and external transmission stays local-only. See
`docs/RD1-audit.md`. Full clean regression: 86 test classes / 726 tests, zero
failures; bounded HTTP/HTML verification PASS on a freshly booted in-memory
instance.

**M9 — RD2: Product deepening and interaction hardening (complete).** RD2
deepens truth and interaction without changing the architecture. Truth: the
landing Application Status badge is actually wired (`appStatusLive`) and
reads `/actuator/health`; the AI badge derives from the authoritative
`AiFailoverService` state via `GET /api/capabilities/ai`; voice joins
OCR/HWR with a truthful `GET /api/capabilities/voice`; source-language
provenance is plumbed end-to-end and stays honestly null until a real
provider records a language. Patients can now correct captured intake
answers as **additive evidence** (`V26__patient_answer_corrections.sql`):
the original answer is immutable, corrections carry actor + timestamp +
reason + a server-side snapshot, every correction is audited
(`PATIENT_CORRECTION`), and the physician workspace shows original and
correction side by side pending an explicit physician review decision.
Display preferences (theme / motion / text size) persist server-side for
authenticated users (`V25__user_display_preferences.sql`) while anonymous
users stay browser-local, seeded through a single flash-free pre-paint
fragment. A shared failure-first feedback layer (`MkFeedback`/`MkFetch`)
replaced all `alert()` usage in professional workflows with `aria-live`
inline errors and a session-expiry recovery bar. The motion token
architecture gained restrained physical/spatial interaction (button press
compression and spring release, card lift, origin-aware dialogs, bubble
entrances) with `prefers-reduced-motion` and Dynamic/Standard/Reduced modes
honored, transform/opacity only, and no `transition: all`. The password
policy is now minimum 6 characters only, applied consistently across
registration, change and provisioning without weakening hashing, lockout,
CSRF or role boundaries. The admin control center gained a filterable,
paginated audit trail (`/admin/audit`) and `SETTINGS_CHANGE` auditing of
every runtime setting update/delete (keys and actors only, never values).
No external API was integrated; OCR/HWR remain `NOT_IMPLEMENTED`. See
`docs/RD2-audit.md`. Full clean regression: 91 test classes / 780 tests,
zero failures, zero errors, zero skipped.

**M10 — RD3: Homepage product surface, printed-document OCR, honest
integrations (complete).** The landing page now derives every capability row
from the running application's authoritative services — OCR, voice, HWR and
HIS/ABDM statuses render the actual deployment state instead of static
claims — and adds explicit patient/physician/administrator entry points with
real, secured routes. Printed-document OCR is implemented against the
Bhashini/ULCA pipeline through the same credential-gated gateway used by the
voice layer (`BhashiniOcrProvider`), flowing into the existing provenance and
physician-review pipeline; without verified credentials it reports
`IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED` everywhere and refuses to fabricate
text, so nothing is presented as live. Handwriting recognition remains
genuinely `NOT_IMPLEMENTED` — ordinary OCR is never relabelled as HWR.
ABDM/HIS stays an honest local-only boundary: the admin console now states
the concrete requirements (HIP registration, ABHA linking, consent-manager
artefacts, gateway credentials) that no deployment here can satisfy, and no
transmission code or connectivity claim was added. See `docs/RD3-audit.md`.

MediKiosk currently includes:

- Authentication/security
- Patient clinical intake
- AYUSH Dashavidha Pariksha
- Ahara-Vihara
- Red flags
- Structured clinical summary
- Physician review
- Persistent completed cases
- Clinical document upload/extraction/findings/timeline
- Adaptive AI clinical conversation
- Multilingual intake
- Voice ASR/TTS integration with a Bhashini adapter and deterministic fallback
- Deterministic repeatable FHIR R4 export with audit trail
- Physician dashboard with persistent accept / amend / reject workspace reviews
- Account lifecycle: registration, login lockout, password change, session control
- Admin console: account management, physician provisioning, system settings
- Patient and physician profile self-service with profile pictures
- Complete clinical history with clinical provenance
- Adaptive conversation planner for finishing a patient history
- Red-flag triage with physician assessment
- Medication interaction screening
- Physician clinical record: triage, clinical summary, consultation
- Magic-byte document validation (PDF / JPEG / PNG content sniffing)
- Honest OCR and handwriting-recognition capability boundary with public status endpoints
- Public website (Home / About / Features / Privacy / Contact) with honest product claims
- Accessible, responsive interface with browser-local preference controls (theme / motion / text size)

## Prerequisites

- JDK 25
- Maven 3.9+
- PostgreSQL 17+ running locally

## Local Setup

### 1. Create the PostgreSQL database and user

```bash
sudo -u postgres psql -c "CREATE USER medikiosk WITH PASSWORD 'medikiosk';"
sudo -u postgres psql -c "CREATE DATABASE medikiosk OWNER medikiosk;"
```

### 2. Configure environment variables (optional; defaults shown)

```bash
export DB_URL="jdbc:postgresql://localhost:5432/medikiosk"
export DB_USERNAME="medikiosk"
export DB_PASSWORD="medikiosk"
export SERVER_PORT="8081"
```

Defaults are already set in `application.yml`, so a stock setup needs no exports.

### 3. Run tests

```bash
mvn clean test
```

### 4. Start the application

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8081`.

### Local profile (no PostgreSQL required)

For a quick start without a local PostgreSQL instance (uses an in-memory PostgreSQL-compatible
H2 database), run:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Verify

- Home page: http://localhost:8081 — MediKiosk welcome page showing application status (**Application Status: UP**)
- Health endpoint: http://localhost:8081/actuator/health

## Project Structure

```
src/main/java/in/devmedi/kiosk/
  MediKioskApplication.java   # Entry point
  config/                     # Security and general configuration
  core/                       # Shared base domain
  health/                     # Health indicator
  home/                       # Home controller
module/                     # Feature modules
      auth/                     # Authentication / security, account lifecycle, profiles
      patient/                  # Patient home, consent, intake, history
      clinical/                 # Clinical dialogue, AYUSH, red flags, summary, adaptive AI, history, provenance, triage
      voice/                    # ASR/TTS (Bhashini adapter + deterministic fallback)
      document/                 # Document upload / extraction / findings / timeline
      physician/                # Physician review, completed cases, assignment, clinical record
      patientsession/           # Patient session state
      encounter/                # Encounter lifecycle
      medication/               # Medication interaction screening
      profile/                  # Patient / physician profiles and picture storage
      admin/                    # Admin console, account management, system settings
      consent/ ai/              # Consent, AI provider adapters
      appointment/ fhir/ abdm/ his/   # Planned / placeholder
      ocr/                      # OCR / handwriting-recognition capability architecture (honest status)
src/main/resources/
  application.yml             # PostgreSQL / Flyway configuration
  templates/home.html         # Home page
  static/css/app.css          # Application CSS
  static/js/app.js            # Application JavaScript
  db/migration/               # Flyway migrations
```

## Voice Input & Question Reading (ASR/TTS)

Patient voice answers and read-aloud questions are served by the `module/voice`
layer. It is built as a *transport* layer: the patient detail is applied only
after a real provider returns real speech — the application never fabricates a
transcript or audio for a provider it is not configured for.

### Provider activation (strictly environment-driven)

Switches and credentials come only from the environment; nothing in
`application.yml` pretends a provider exists:

| Setting                      | Environment variable            | Default      |
|------------------------------|---------------------------------|--------------|
| ASR provider                 | `MEDIKIOSK_ASR_PROVIDER`        | `unavailable`|
| TTS provider                 | `MEDIKIOSK_TTS_PROVIDER`        | `unavailable`|
| Bhashini user id             | `MEDIKIOSK_BHASHINI_USER_ID`    | *(none)*     |
| Bhashini API key             | `MEDIKIOSK_BHASHINI_API_KEY`    | *(none)*     |
| Bhashini pipeline id         | `MEDIKIOSK_BHASHINI_PIPELINE_ID`| *(none)*     |
| Bhashini config API URL      | `MEDIKIOSK_BHASHINI_API_URL`    | ULCA `/model/getModelsPipeline` endpoint |

A provider is engaged only when its switch is `bhashini` **and** its credentials
are complete. Otherwise the deterministic `UNAVAILABLE` fallback answers and the
patient simply types. Request timeouts are the shared 10-second HTTP client
configured for AI providers.

### Endpoints

- `POST /patient/intake/voice/asr` — multipart audio (`audio`, optional
  `language`); returns `{status, transcript, language}`. Boundary rules: empty or
  non-audio upload → `400`, > 10 MB → `413`, unsupported language → `400`.
- `POST /patient/intake/voice/tts` — JSON `{text, language}`; returns `audio/wav`
  bytes on success. Blank or > 500-char text → `400`.
- Common responses: `TRANSCRIBED`/`SYNTHESIZED` → `200`, `UNAVAILABLE` → `503`,
  provider failure → `502` (active provider mode only).

Both endpoints live under `/patient/**` so the existing patient role and CSRF
protections apply unchanged.

### Setup handoff (WC)

For the full manual setup walk-through — where each credential comes from,
local verification commands, the OCR operator-verification requirement, the
public-apis catalogue verdict on additional voice providers, and what cannot
be tested until credentials exist — see **`docs/WC-setup-guide.md`**. Startup
now also logs a `voice.*` warning naming the exact missing environment variable
whenever voice is misconfigured, and the capability endpoints plus the landing
and admin pages carry the same secret-free setup hints.

### Languages

Five languages are selectable: English (`en-IN`), Hindi (`hi-IN`), Telugu
(`te-IN`), Tamil (`ta-IN`), Kannada (`kn-IN`). The selection is stored in the
patient session (`/patient/intake/language`) and threaded into every voice
request; each recorded clinical answer and its on-screen translation persist the
language used (`answer_language`).

## Notes

- `spring.jpa.hibernate.ddl-auto=validate` + Flyway together manage the schema; no auto DDL.
- The `local` and `test` profiles use an in-memory PostgreSQL-compatible H2 database for
  development and testing; PostgreSQL remains the production datasource.
