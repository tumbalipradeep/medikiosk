/**
 * Voice and multilingual foundation for MediKiosk patient intake.
 *
 * <p>This module provides the provider-neutral language model
 * ({@link in.devmedi.kiosk.module.voice.language}) and the provider-neutral
 * speech abstractions ({@link in.devmedi.kiosk.module.voice.speech}) used by
 * later M3 phases to connect real Indian-language ASR/TTS providers.</p>
 *
 * <p>There are deliberately no vendor bindings here. Actual Bhashini/ULCA or
 * other provider integrations belong to a later phase; in this checkpoint the
 * deterministic fallbacks safely report unavailable/unsupported and never
 * invent patient speech or pretend audio was produced.</p>
 */
package in.devmedi.kiosk.module.voice;