-- clients.name had the same hard-UNIQUE problem as clients.code (fixed in V47).
-- Soft-deleted rows kept the name occupied, blocking re-creation.
ALTER TABLE clients DROP CONSTRAINT IF EXISTS clients_name_key;

CREATE UNIQUE INDEX IF NOT EXISTS clients_name_active_idx
    ON clients (name) WHERE active = true;
