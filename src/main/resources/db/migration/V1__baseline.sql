-- MediKiosk baseline migration.
-- Checkpoint 1 establishes schema management only; no functional tables yet.
-- Real module tables and seed data land in later checkpoints.
CREATE TABLE IF NOT EXISTS schema_status (
    version INTEGER NOT NULL
);
