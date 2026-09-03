CREATE TABLE agent_tool_calls (
  id            BIGSERIAL PRIMARY KEY,
  run_id        BIGINT REFERENCES agent_runs(id) ON DELETE CASCADE,
  tool_name     VARCHAR(100),
  args          JSONB,
  result        JSONB,
  hitl_required BOOLEAN DEFAULT false,
  hitl_outcome  VARCHAR(20),
  hitl_note     TEXT,
  called_at     TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_atc_run ON agent_tool_calls(run_id);
