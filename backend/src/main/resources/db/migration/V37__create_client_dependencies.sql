CREATE TABLE client_dependencies (
  id           BIGSERIAL PRIMARY KEY,
  client_id    BIGINT REFERENCES clients(id),
  title        VARCHAR(200) NOT NULL,
  description  TEXT,
  dep_type     VARCHAR(20),
  issue_id     BIGINT REFERENCES jira_issues(id),
  raised_at    DATE,
  resolved_at  DATE,
  status       VARCHAR(20) DEFAULT 'OPEN'
);

CREATE INDEX idx_dep_client ON client_dependencies(client_id);
