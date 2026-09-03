CREATE TABLE uat_cycles (
  id              BIGSERIAL PRIMARY KEY,
  issue_id        BIGINT REFERENCES jira_issues(id),
  cycle_number    INTEGER NOT NULL,
  started_at      TIMESTAMP,
  completed_at    TIMESTAMP,
  sign_off_status VARCHAR(20) DEFAULT 'PENDING',
  signed_off_by   VARCHAR(100),
  signed_off_at   TIMESTAMP,
  env_snapshot    VARCHAR(50),
  notes           TEXT
);

CREATE INDEX idx_uat_issue ON uat_cycles(issue_id);
