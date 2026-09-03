-- The hard UNIQUE constraint on clients.code blocks soft-deleted rows from being
-- re-used. Replace it with a partial unique index scoped to active=true rows only.
ALTER TABLE clients DROP CONSTRAINT IF EXISTS clients_code_key;

CREATE UNIQUE INDEX IF NOT EXISTS clients_code_active_idx
    ON clients (code) WHERE active = true;
