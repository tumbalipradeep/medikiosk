-- MediKiosk Checkpoint 2: development/demo users.
-- Passwords are BCrypt hashes (never plaintext):
--   patient   / patient123
--   physician / physician123
--   admin     / admin123
-- These are synthetic demo identities for local development only; no real
-- personal data is stored.

INSERT INTO users (username, password, display_name, role, enabled, created_at, updated_at)
VALUES ('patient', '$2a$10$xVtr2X.QvKLsn8HBQP7Ac.qixWbgqX8JOYO1ranOqZkR9t3Ynj5aW', 'Demo Patient', 'PATIENT', TRUE, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z');

INSERT INTO users (username, password, display_name, role, enabled, created_at, updated_at)
VALUES ('physician', '$2a$10$FOdrHryCCNUf2u.useLYHOME6F7ZAcNtk5XfBVFDbGHI/CZdWCuwO', 'Dr. Demo Physician', 'PHYSICIAN', TRUE, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z');

INSERT INTO users (username, password, display_name, role, enabled, created_at, updated_at)
VALUES ('admin', '$2a$10$/q8lbknDZ05huKup9DG/ruDK9a2s70HInfxGlhrtuTpOwqFXm.EQS', 'MediKiosk Administrator', 'ADMIN', TRUE, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z');