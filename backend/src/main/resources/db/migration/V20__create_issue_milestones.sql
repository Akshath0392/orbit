CREATE TABLE issue_milestones (
  id               BIGSERIAL PRIMARY KEY,
  issue_id         BIGINT REFERENCES jira_issues(id),
  milestone_type   VARCHAR(20),
  target_date      DATE,
  actual_date      DATE,
  is_tbc           BOOLEAN DEFAULT FALSE,
  status           VARCHAR(20),
  source           VARCHAR(20) DEFAULT 'JIRA_FIELD'
);

CREATE INDEX idx_ms_issue ON issue_milestones(issue_id);
