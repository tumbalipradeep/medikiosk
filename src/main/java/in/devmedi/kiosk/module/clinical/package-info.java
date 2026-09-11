/**
 * clinical module package for MediKiosk.
 * Hosts the deterministic clinical dialogue foundation (sections, questions,
 * SOCRATES-style planner), the red-flag safety layer, and the AYUSH
 * Dashavidha Pariksha assessment sequence for the patient intake flow.
 * Also hosts the AI clinical conversation orchestration (module/clinical/ai),
 * which rewrites only the wording of deterministic next questions and always
 * falls back to the deterministic backbone.
 */
package in.devmedi.kiosk.module.clinical;