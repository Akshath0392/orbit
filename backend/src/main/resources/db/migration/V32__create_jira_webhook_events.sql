CREATE TABLE jira_webhook_events (
  id           BIGSERIAL PRIMARY KEY,
  webhook_id   VARCHAR(100) UNIQUE,
  event_type   VARCHAR(50),
  issue_key    VARCHAR(30),
  received_at  TIMESTAMP DEFAULT NOW(),
  processed    BOOLEAN DEFAULT FALSE
);
