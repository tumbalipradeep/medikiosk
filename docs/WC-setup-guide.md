# WC Setup Guide — Provider Integrations

Manual setup handoff for WC (Website Connecting). **Every environment-variable
name below is verified against source** (`VoiceProperties`, `AiProviderConfig`,
`application.yml`); none are invented. Values are never stored in the
repository — set them in your deployment environment only.

Status vocabulary used below (same discipline as `/api/capabilities/*`):

| State | Meaning |
|---|---|
| **Implemented** | The integration code exists and is wired. |
| **Configured** | Switch set AND credentials present (read from env at startup). |
| **Credential-verified** | An operator has confirmed the live provider works end to end. Only ever produced by a human verification pass, never by code. |
| **Live** | Configured AND credential-verified. |

---

## 1. Conversational AI (Groq / Gemini / OpenRouter) — implemented; needs keys

Failover order (from `medikiosk.ai.failover-order`): `groq → gemini → openrouter`.
Each provider is enabled when its key is present in the environment.

| Environment variable | Read by | Where to obtain |
|---|---|---|
| `GROQ_API_KEY` | `System.getenv` in `AiProviderConfig` | Groq console (console.groq.com) → API keys |
| `GEMINI_API_KEY` | `System.getenv` in `AiProviderConfig` | Google AI Studio → Get API key |
| `OPENROUTER_API_KEY` | `System.getenv` in `AiProviderConfig` | OpenRouter → Keys (openrouter.ai/keys) |

Notes:
- Models, base URLs, timeouts and token limits are already set in
  `application.yml` (`medikiosk.ai.providers.*`) — no additional configuration
  is required to bring a provider up; the key is the only missing piece.
- With no keys at all, AI assistance honestly reports
  `anyProviderEnabled=false` on `/api/capabilities/ai` and the deterministic
  non-AI question flow is used. No clinical path depends on AI.

Verify locally:

```bash
curl -m 10 http://localhost:8081/api/capabilities/ai
# expect: each provider listed with enabled=true once its key is exported
```

## 2. Patient voice — ASR (speech-to-text) and TTS (read-aloud) — implemented; needs Bhashini credentials

One provider (Bhashini/ULCA) serves both directions; the deterministic
`unavailable` fallback (patient types instead) engages whenever the predicate
below is not satisfied.

| Environment variable | Binds to | Purpose |
|---|---|---|
| `MEDIKIOSK_ASR_PROVIDER` | `medikiosk.asr-provider` | Set to `bhashini` to enable speech-to-text |
| `MEDIKIOSK_TTS_PROVIDER` | `medikiosk.tts-provider` | Set to `bhashini` to enable read-aloud |
| `MEDIKIOSK_BHASHINI_USER_ID` | `medikiosk.bhashini.user-id` | ULCA user id |
| `MEDIKIOSK_BHASHINI_API_KEY` | `medikiosk.bhashini.api-key` | ULCA API key |
| `MEDIKIOSK_BHASHINI_PIPELINE_ID` | `medikiosk.bhashini.pipeline-id` | Selected pipeline id |
| `MEDIKIOSK_BHASHINI_API_URL` | `medikiosk.bhashini.api-url` | Optional; defaults to the ULCA `getModelsPipeline` endpoint |

A real provider engages **only when the switch is set AND all three credential
variables are non-blank** (`VoiceProperties.Bhashini.isComplete()`); otherwise
the UNAVAILABLE fallback is bound and `MEDIKIOSK_BHASHINI_API_URL` is unused.

Manual setup (Bhashini/ULCA):
1. Register on the Bhashini/ULCA portal and create an application; you receive
   a ULCA **user id** and **API key**.
2. Call the pipeline-discovery endpoint (`getModelsPipeline`) with those
   credentials to list available ASR/TTS pipelines and choose a **pipeline id**
   for each service you want live.
3. Export the variables above (never commit them) and restart.

Verify locally:

```bash
curl -m 10 http://localhost:8081/api/capabilities/voice
# expect: "asrProvider":"bhashini","asrAvailable":true (and tts) once configured
```

On startup the application logs a `voice.*` warning naming exactly which
variable to set when voice is not live (switch unknown/off, or credentials
incomplete). The response also carries a secret-free `setupHint` saying the
same thing.

**Cannot be verified until credentials exist:** actual transcription quality
per language, audio format acceptance (`asr-audio-format`/`asr-sampling-rate`
defaults: wav / 16000 Hz), TTS voice quality, and per-language
supported/unsupported behaviour. All of this requires the live ULCA service.

## 3. Printed-document OCR (Bhashini pipeline) — implemented; needs credentials + operator verification

Selected engine = `medikiosk.ocr.provider` (env `MEDIKIOSK_OCR_PROVIDER`).
Default `local-dev` is the honest no-engine seam.

| Environment variable | Purpose |
|---|---|
| `MEDIKIOSK_OCR_PROVIDER` | Set to `bhashini` to select the Bhashini OCR engine |
| `MEDIKIOSK_BHASHINI_USER_ID` / `MEDIKIOSK_BHASHINI_API_KEY` / `MEDIKIOSK_BHASHINI_PIPELINE_ID` | Same Bhashini credentials as voice (choose an OCR pipeline id) |

Important honesty rules already enforced in code:
- The Bhashini OCR engine reports
  `IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED` **even with credentials present**
  until you, the operator, verify a real extraction end to end. It is never
  reported as live automatically.
- Extracted text always flows through physician review; failures are recorded
  as failed/unsupported outcomes — no text is ever fabricated.

Manual verification pass (required to treat OCR as live): with credentials set
and `MEDIKIOSK_OCR_PROVIDER=bhashini`, upload a printed report as a patient,
trigger extraction from the physician workspace, and confirm the transcript
against the document. Record the result; only then should the deployment be
described as OCR-live.

Verify locally:

```bash
curl -m 10 http://localhost:8081/api/capabilities/ocr
# expect: engines list contains the bhashini engine; overall status is honest
```

## 4. Handwriting recognition — NOT_IMPLEMENTED (deliberate)

No suitable provider exists in the public-apis catalogue used by this project,
and ordinary OCR is never relabelled as HWR. `/api/capabilities/hwr` reports
`NOT_IMPLEMENTED`. Nothing to configure. (CW scope.)

## 5. ABDM / HIS transmission — Local only (boundary documented, not integrated)

Live ABDM requires (none of which are configured in this deployment):
HIP/HIU registration with the ABDM ecosystem, ABHA linking capability,
consent-manager artefacts and gateway/sandbox credentials, plus a real export
transport replacing `LocalOnlyExportTransport`.

Until you complete that registration and provide credentials, the application
stays honestly local: no transmission code will claim connectivity, and the
admin console states these requirements next to the "Local only" status.
Nothing can be tested externally until registration artefacts exist.

## 6. Deployment / infrastructure variables (not provider features)

| Environment variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `SERVER_PORT` | HTTP port (default 8081) |
| `MEDIKIOSK_UPLOAD_DIR` | Clinical document storage location |

## 7. Three-voice-providers requirement — catalogue verdict

The project's API constraint allows only providers from the
`public-apis` catalogue. That catalogue (verified against its master README)
contains **no speech APIs at all** — no ASR, no TTS, no translation entries.
Therefore a second or third genuinely distinct voice provider **cannot be
established under the constraint**. Aliases of the same provider are not
counted. WC keeps: one real provider (Bhashini) + the deterministic
`unavailable` fallback. The provider-neutral `SpeechRecognitionService` /
`SpeechSynthesisService` seams accept additional engines in CW if a suitable
provider is later approved.

## 8. What cannot be tested until you act

- ASR/TTS live behaviour (needs Bhashini credentials + pipeline ids).
- OCR live extraction and pipeline verification (needs credentials + your
  manual verification pass, Section 3).
- AI answer quality with real models (needs at least one AI key; everything
  else about AI is testable).
- Any external connectivity (ABDM/HIS) — genuinely out of scope until
  registration exists.
