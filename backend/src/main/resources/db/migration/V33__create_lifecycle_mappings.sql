CREATE TABLE lifecycle_mappings (
  id          BIGSERIAL PRIMARY KEY,
  jira_status VARCHAR(50) NOT NULL,
  issue_type  VARCHAR(20) NOT NULL,
  gauge_stage VARCHAR(50) NOT NULL,
  UNIQUE(jira_status, issue_type)
);
