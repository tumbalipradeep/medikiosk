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
      auth/                     # Authentication / security
      patient/                  # Patient home, consent, intake
      clinical/                 # Clinical dialogue, AYUSH, red flags, summary, adaptive AI
      voice/                    # ASR/TTS (Bhashini adapter + deterministic fallback)
      document/                 # Document upload / extraction / findings / timeline
      physician/                # Physician review, completed cases
      patientsession/           # Patient session state
      consent/ admin/ ai/       # Consent, admin home, AI provider adapters
      appointment/ fhir/ abdm/ his/ ocr/   # Planned / placeholder
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
