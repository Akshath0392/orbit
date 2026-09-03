CREATE TABLE notification_events (
  id              BIGSERIAL   PRIMARY KEY,
  rule_id         BIGINT      REFERENCES notification_rules(id),
  project_id      BIGINT      REFERENCES projects(id),
  phase_status_id BIGINT      REFERENCES phase_statuses(id),
  phase           VARCHAR(20),
  event_type      VARCHAR(30),
  recipient_email VARCHAR(255),
  recipient_name  VARCHAR(255),
  slack_msg_ts    VARCHAR(50),
  user_response   VARCHAR(30),
  responded_at    TIMESTAMP,
  sent_at         TIMESTAMP   DEFAULT NOW()
);

CREATE INDEX idx_ne_project    ON notification_events(project_id);
CREATE INDEX idx_ne_event_type ON notification_events(event_type);
CREATE INDEX idx_ne_sent_at    ON notification_events(sent_at);
