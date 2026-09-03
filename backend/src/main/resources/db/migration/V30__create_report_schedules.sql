CREATE TABLE report_schedules (
  id                         BIGSERIAL PRIMARY KEY,
  report_type                VARCHAR(50),
  client_id                  BIGINT REFERENCES clients(id),
  cron_expression            VARCHAR(50),
  recipients                 TEXT[],
  include_client_safe_filter BOOLEAN DEFAULT TRUE,
  created_by                 BIGINT REFERENCES app_users(id),
  active                     BOOLEAN DEFAULT TRUE,
  last_run_at                TIMESTAMP,
  next_run_at                TIMESTAMP
);
