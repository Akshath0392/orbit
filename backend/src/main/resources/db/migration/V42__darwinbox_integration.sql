-- Darwin emp ID on app users for cross-system mapping
ALTER TABLE app_users ADD COLUMN darwin_emp_id VARCHAR(50);
CREATE UNIQUE INDEX idx_users_darwin_emp ON app_users(darwin_emp_id) WHERE darwin_emp_id IS NOT NULL;

-- Sync run history (mirrors jira_webhook_events pattern)
CREATE TABLE darwin_sync_runs (
  id             BIGSERIAL PRIMARY KEY,
  sync_type      VARCHAR(20)  NOT NULL,  -- FULL | DELTA
  status         VARCHAR(20)  NOT NULL,  -- SUCCESS | FAILED | IN_PROGRESS
  records_pulled INTEGER      DEFAULT 0,
  error_message  TEXT,
  started_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
  completed_at   TIMESTAMP
);

CREATE INDEX idx_darwin_sync_started ON darwin_sync_runs(started_at DESC);

-- Leave records pulled from Darwinbox
CREATE TABLE leave_records (
  id               BIGSERIAL PRIMARY KEY,
  user_id          BIGINT REFERENCES app_users(id),
  darwin_emp_id    VARCHAR(50)  NOT NULL,
  darwin_leave_id  VARCHAR(100) NOT NULL UNIQUE,
  leave_type       VARCHAR(50),          -- Annual, Sick, Casual, Comp-off, etc.
  start_date       DATE         NOT NULL,
  end_date         DATE         NOT NULL,
  working_days     INTEGER      DEFAULT 1,
  status           VARCHAR(20)  NOT NULL, -- PENDING | APPROVED | REJECTED | CANCELLED
  remarks          TEXT,
  synced_at        TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX idx_leave_user       ON leave_records(user_id);
CREATE INDEX idx_leave_dates      ON leave_records(start_date, end_date);
CREATE INDEX idx_leave_status     ON leave_records(status);
CREATE INDEX idx_leave_darwin_emp ON leave_records(darwin_emp_id);

-- Seed emp ID mappings for existing users
UPDATE app_users SET darwin_emp_id = 'EMP001' WHERE email = 'priya.k@gauge.io';
UPDATE app_users SET darwin_emp_id = 'EMP002' WHERE email = 'amit.s@gauge.io';
UPDATE app_users SET darwin_emp_id = 'EMP003' WHERE email = 'rajesh.n@gauge.io';
UPDATE app_users SET darwin_emp_id = 'EMP004' WHERE email = 'kavitha.r@gauge.io';
UPDATE app_users SET darwin_emp_id = 'EMP005' WHERE email = 'dev.l@gauge.io';

-- Seed sample leave records (covers June–August 2026)
INSERT INTO leave_records (user_id, darwin_emp_id, darwin_leave_id, leave_type, start_date, end_date, working_days, status) VALUES
  ((SELECT id FROM app_users WHERE email='dev.l@gauge.io'),       'EMP005', 'DBX-L-1001', 'Annual Leave', '2026-06-23', '2026-06-26', 4, 'APPROVED'),
  ((SELECT id FROM app_users WHERE email='kavitha.r@gauge.io'),   'EMP004', 'DBX-L-1002', 'Sick Leave',   '2026-06-15', '2026-06-16', 2, 'APPROVED'),
  ((SELECT id FROM app_users WHERE email='priya.k@gauge.io'),     'EMP001', 'DBX-L-1003', 'Annual Leave', '2026-07-07', '2026-07-11', 5, 'APPROVED'),
  ((SELECT id FROM app_users WHERE email='amit.s@gauge.io'),      'EMP002', 'DBX-L-1004', 'Annual Leave', '2026-07-21', '2026-07-25', 5, 'PENDING'),
  ((SELECT id FROM app_users WHERE email='dev.l@gauge.io'),       'EMP005', 'DBX-L-1005', 'Casual Leave', '2026-08-03', '2026-08-04', 2, 'APPROVED');

-- Seed initial sync run record
INSERT INTO darwin_sync_runs (sync_type, status, records_pulled, started_at, completed_at) VALUES
  ('FULL',  'SUCCESS', 5, NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours' + INTERVAL '8 seconds'),
  ('DELTA', 'SUCCESS', 0, NOW() - INTERVAL '1 hour',  NOW() - INTERVAL '1 hour'  + INTERVAL '1 second');
