-- ── Generic HRMS connector config (replaces darwinbox_config) ────────────────
-- One row: which provider is active + provider-specific settings as JSONB
-- (shape defined by the connector's settings descriptor; secrets included).
CREATE TABLE hrms_config (
  id           BIGSERIAL PRIMARY KEY,
  provider_key VARCHAR(50),
  settings     JSONB       NOT NULL DEFAULT '{}'::jsonb,
  enabled      BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMP,
  updated_by   VARCHAR(150)
);

-- Carry over any existing Darwinbox configuration.
INSERT INTO hrms_config (provider_key, settings, enabled, created_at, updated_at, updated_by)
SELECT 'darwinbox',
       jsonb_strip_nulls(jsonb_build_object(
         'baseUrl',       base_url,
         'companyId',     company_id,
         'apiKey',        api_key,
         'authType',      auth_type,
         'syncDaysAhead', sync_days_ahead,
         'webhookSecret', webhook_secret)),
       COALESCE(enabled, FALSE),
       NOW(),
       updated_at,
       updated_by
FROM darwinbox_config
ORDER BY id ASC
LIMIT 1;

DROP TABLE darwinbox_config;

-- ── Provider-agnostic sync-run history ───────────────────────────────────────
ALTER TABLE darwin_sync_runs RENAME TO hrms_sync_runs;
ALTER INDEX idx_darwin_sync_started RENAME TO idx_hrms_sync_started;
ALTER SEQUENCE darwin_sync_runs_id_seq RENAME TO hrms_sync_runs_id_seq;
