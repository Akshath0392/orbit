CREATE TABLE alerts (
  id               BIGSERIAL PRIMARY KEY,
  alert_type       VARCHAR(50),
  severity         VARCHAR(10),
  issue_id         BIGINT REFERENCES jira_issues(id),
  client_id        BIGINT REFERENCES clients(id),
  project_id       BIGINT REFERENCES projects(id),
  title            VARCHAR(200),
  detail           TEXT,
  source_agent     VARCHAR(50),
  status           VARCHAR(20) DEFAULT 'OPEN',
  owner_name       VARCHAR(100),
  follow_up_date   DATE,
  mitigation_note  TEXT,
  ai_explanation   TEXT,
  created_at       TIMESTAMP DEFAULT NOW(),
  resolved_at      TIMESTAMP
);

CREATE INDEX idx_al_client   ON alerts(client_id);
CREATE INDEX idx_al_status   ON alerts(status);
CREATE INDEX idx_al_severity ON alerts(severity);
