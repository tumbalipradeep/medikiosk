/**
 * Deterministic, rule-based structured findings extracted from clinical
 * document text (M1.3).
 *
 * <p>No LLM or external AI is involved. Parsing is fully deterministic: the
 * same extracted text always produces the same structured findings. Raw source
 * snippets are preserved alongside every decoded value so physicians can trace
 * each finding back to the originating document line, and nothing is invented
 * where the text is ambiguous or unparseable.</p>
 */
package in.devmedi.kiosk.module.document.findings;