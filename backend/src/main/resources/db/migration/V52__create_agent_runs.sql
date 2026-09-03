CREATE TABLE agent_runs (
  id              BIGSERIAL PRIMARY KEY,
  agent_id        BIGINT REFERENCES agent_definitions(id),
  project_id      BIGINT REFERENCES projects(id),
  triggered_by    VARCHAR(30),
  status          VARCHAR(20) DEFAULT 'RUNNING',
  input_context   JSONB,
  output_summary  TEXT,
  error_message   TEXT,
  tokens_used     INTEGER,
  duration_ms     INTEGER,
  started_at      TIMESTAMP DEFAULT NOW(),
  completed_at    TIMESTAMP
);
CREATE INDEX idx_ar_agent ON agent_runs(agent_id, started_at DESC);
