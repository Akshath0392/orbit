CREATE TABLE issue_transitions (
  id             BIGSERIAL PRIMARY KEY,
  issue_id       BIGINT REFERENCES jira_issues(id),
  from_status    VARCHAR(50),
  to_status      VARCHAR(50),
  transitioned_at TIMESTAMP,
  transitioned_by VARCHAR(100)
);

CREATE INDEX idx_tr_issue ON issue_transitions(issue_id);
