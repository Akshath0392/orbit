-- ── Darwinbox config stored in DB (mirrors jira_config pattern) ────────────────
CREATE TABLE darwinbox_config (
  id           BIGSERIAL PRIMARY KEY,
  base_url     VARCHAR(255),
  api_key      VARCHAR(2000),
  company_id   VARCHAR(100),
  auth_type    VARCHAR(20)  DEFAULT 'API_KEY', -- API_KEY | BEARER | HMAC
  enabled      BOOLEAN      DEFAULT FALSE,
  sync_cron    VARCHAR(60)  DEFAULT '0 0 */4 * * *',
  sync_days_ahead INTEGER   DEFAULT 90,
  webhook_secret VARCHAR(200),
  updated_at   TIMESTAMP,
  updated_by   VARCHAR(150)
);

-- ── Attendance records ───────────────────────────────────────────────────────
CREATE TABLE attendance_records (
  id               BIGSERIAL PRIMARY KEY,
  user_id          BIGINT REFERENCES app_users(id),
  darwin_emp_id    VARCHAR(50)  NOT NULL,
  attendance_date  DATE         NOT NULL,
  check_in         TIME,
  check_out        TIME,
  working_hours    DECIMAL(5,2),
  status           VARCHAR(30),  -- Present | Absent | Late | Half-day | WFH | Holiday
  synced_at        TIMESTAMP    DEFAULT NOW(),
  UNIQUE(darwin_emp_id, attendance_date)
);

CREATE INDEX idx_attendance_user   ON attendance_records(user_id);
CREATE INDEX idx_attendance_date   ON attendance_records(attendance_date DESC);
CREATE INDEX idx_attendance_status ON attendance_records(status);

-- ── Leave balances ───────────────────────────────────────────────────────────
CREATE TABLE leave_balances (
  id             BIGSERIAL PRIMARY KEY,
  user_id        BIGINT REFERENCES app_users(id),
  darwin_emp_id  VARCHAR(50)  NOT NULL,
  leave_type     VARCHAR(60)  NOT NULL,
  total_days     DECIMAL(5,1) DEFAULT 0,
  taken_days     DECIMAL(5,1) DEFAULT 0,
  pending_days   DECIMAL(5,1) DEFAULT 0,
  remaining_days DECIMAL(5,1) DEFAULT 0,
  synced_at      TIMESTAMP    DEFAULT NOW(),
  UNIQUE(darwin_emp_id, leave_type)
);

CREATE INDEX idx_balance_user ON leave_balances(user_id);
