CREATE TABLE agent_memory (
  id          BIGSERIAL PRIMARY KEY,
  agent_id    BIGINT REFERENCES agent_definitions(id) ON DELETE CASCADE,
  project_id  BIGINT REFERENCES projects(id),
  memory_type VARCHAR(20) DEFAULT 'FACT',
  mem_key     VARCHAR(200),
  mem_value   TEXT,
  created_at  TIMESTAMP DEFAULT NOW(),
  expires_at  TIMESTAMP
);
CREATE INDEX idx_am_agent_project ON agent_memory(agent_id, project_id);
CREATE INDEX idx_am_key ON agent_memory(agent_id, mem_key);
