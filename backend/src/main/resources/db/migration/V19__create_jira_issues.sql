CREATE TABLE jira_issues (
  id                       BIGSERIAL PRIMARY KEY,
  issue_key                VARCHAR(30)  NOT NULL UNIQUE,
  summary                  TEXT,
  issue_type               VARCHAR(20),
  jira_status              VARCHAR(50),
  lifecycle_stage          VARCHAR(50),
  priority                 VARCHAR(20),
  severity                 VARCHAR(10),
  assignee_name            VARCHAR(100),
  project_id               BIGINT REFERENCES projects(id),
  client_id                BIGINT REFERENCES clients(id),
  fix_version              VARCHAR(100),
  created_at               TIMESTAMP,
  updated_at               TIMESTAMP,
  resolved_at              TIMESTAMP,
  last_synced_at           TIMESTAMP,
  reopen_count             INTEGER DEFAULT 0,
  hold_reason              TEXT,
  sla_remaining_hours      DECIMAL(6,2),
  sla_status               VARCHAR(20),
  bert_suggested_severity  VARCHAR(10),
  bert_suggested_owner     VARCHAR(100),
  bert_suggestion_accepted BOOLEAN,
  raw_jira_json            JSONB
);

CREATE INDEX idx_ji_client    ON jira_issues(client_id);
CREATE INDEX idx_ji_stage     ON jira_issues(lifecycle_stage);
CREATE INDEX idx_ji_type      ON jira_issues(issue_type);
CREATE INDEX idx_ji_updated   ON jira_issues(updated_at DESC);
