CREATE TABLE alert_actions (
  id           BIGSERIAL PRIMARY KEY,
  alert_id     BIGINT REFERENCES alerts(id),
  action_type  VARCHAR(20),
  performed_by VARCHAR(100),
  note         TEXT,
  performed_at TIMESTAMP DEFAULT NOW()
);
