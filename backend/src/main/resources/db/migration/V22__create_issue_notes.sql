CREATE TABLE issue_notes (
  id               BIGSERIAL PRIMARY KEY,
  issue_id         BIGINT REFERENCES jira_issues(id),
  text             TEXT NOT NULL,
  is_client_safe   BOOLEAN DEFAULT FALSE,
  created_by       VARCHAR(100),
  created_at       TIMESTAMP DEFAULT NOW()
);
