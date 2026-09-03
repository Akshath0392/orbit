CREATE TABLE agent_project_summaries (
  id            BIGSERIAL PRIMARY KEY,
  project_id    BIGINT REFERENCES projects(id) UNIQUE,
  summary_text  TEXT,
  updated_at    TIMESTAMP DEFAULT NOW(),
  token_count   INTEGER
);

CREATE TABLE agent_decision_log (
  id            BIGSERIAL PRIMARY KEY,
  agent_name    VARCHAR(50),
  trigger_event TEXT,
  proposal_json JSONB,
  outcome       VARCHAR(20),
  outcome_note  TEXT,
  tokens_used   INTEGER,
  decided_by    VARCHAR(100),
  decided_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_adl_agent   ON agent_decision_log(agent_name);
CREATE INDEX idx_adl_outcome ON agent_decision_log(outcome);
