-- Snapshot reporting agent (Phase A): tracked artifacts of Radar / other page renders
-- produced by the Playwright sidecar. Idempotency via partial unique index on dedup_key
-- while a render is in-flight; recent READY rows serve as a 5-min cache.

CREATE TABLE agent_snapshots (
  id              BIGSERIAL PRIMARY KEY,
  agent_run_id    BIGINT REFERENCES agent_runs(id),
  user_id         BIGINT NOT NULL REFERENCES app_users(id),
  dedup_key       VARCHAR(64) NOT NULL,
  kind            VARCHAR(32) NOT NULL,                  -- 'RADAR' for v1
  portfolio_id    BIGINT REFERENCES portfolios(id),
  lens            VARCHAR(32) NOT NULL,
  project_id      BIGINT REFERENCES projects(id),
  state           VARCHAR(16) NOT NULL,                  -- PENDING | RUNNING | READY | FAILED
  png_path        TEXT,
  pdf_path        TEXT,
  error_message   TEXT,
  created_at      TIMESTAMP NOT NULL DEFAULT now(),
  completed_at    TIMESTAMP,
  expires_at      TIMESTAMP NOT NULL DEFAULT (now() + INTERVAL '7 days')
);

CREATE INDEX idx_snapshot_user_created
  ON agent_snapshots (user_id, created_at DESC);

CREATE INDEX idx_snapshot_dedup_completed
  ON agent_snapshots (dedup_key, completed_at DESC)
  WHERE state = 'READY';

-- Coalesces concurrent submits: at most ONE in-flight row per dedup key.
-- Inserts violating this throw DataIntegrityViolationException → SnapshotService
-- treats it as "already in progress" and returns the existing row.
CREATE UNIQUE INDEX uq_snapshot_inflight
  ON agent_snapshots (dedup_key)
  WHERE state IN ('PENDING', 'RUNNING');
