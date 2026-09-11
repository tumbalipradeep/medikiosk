/**
 * Clinical document management for MediKiosk.
 *
 * <p>Handles patient uploads of clinical documents (PDF, JPEG, PNG) against a
 * completed intake case. Document binaries are stored on the local filesystem
 * under generated UUID filenames, while metadata (original name, size, content
 * type, upload time) is persisted in PostgreSQL and surfaced to the physician
 * review flow. Uploaded files are never executed or served unauthenticated, and
 * no OCR, AI analysis, or abnormal detection is performed here.</p>
 */
package in.devmedi.kiosk.module.document;