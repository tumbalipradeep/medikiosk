/**
 * ai module package for MediKiosk.
 * Hosts the AI provider transport and the provider-neutral clinical conversation
 * contracts: failover-ordered provider adapters (Groq, Gemini, OpenRouter) with
 * typed failures, the compact ClinicalAiRequest/ClinicalAiResponse exchange, and
 * the structured, strictly validated AiQuestionResponse contract consumed by the
 * clinical conversation orchestration (module/clinical/ai).
 */
package in.devmedi.kiosk.module.ai;