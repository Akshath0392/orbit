CREATE TABLE wfh_records (
  id              BIGSERIAL    PRIMARY KEY,
  user_id         BIGINT       REFERENCES app_users(id),
  darwin_emp_id   VARCHAR(50)  NOT NULL,
  darwin_wfh_id   VARCHAR(100) NOT NULL UNIQUE,
  wfh_date        DATE         NOT NULL,
  wfh_type        VARCHAR(20)  DEFAULT 'FULL_DAY',
  status          VARCHAR(20)  NOT NULL,
  reason          TEXT,
  synced_at       TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX idx_wfh_user   ON wfh_records(user_id);
CREATE INDEX idx_wfh_date   ON wfh_records(wfh_date);
CREATE INDEX idx_wfh_status ON wfh_records(status);
CREATE INDEX idx_wfh_emp    ON wfh_records(darwin_emp_id);

INSERT INTO wfh_records (user_id, darwin_emp_id, darwin_wfh_id, wfh_date, wfh_type, status, reason) VALUES
  ((SELECT id FROM app_users WHERE email='priya.k@gauge.io'   LIMIT 1), 'EMP001', 'DBX-WFH-1001', '2026-06-22', 'FULL_DAY',    'APPROVED', 'Focus work — design review'),
  ((SELECT id FROM app_users WHERE email='dev.l@gauge.io'     LIMIT 1), 'EMP005', 'DBX-WFH-1002', '2026-06-23', 'HALF_DAY_AM', 'APPROVED', NULL),
  ((SELECT id FROM app_users WHERE email='amit.s@gauge.io'    LIMIT 1), 'EMP002', 'DBX-WFH-1003', '2026-06-24', 'FULL_DAY',    'PENDING',  'Team sync from home'),
  ((SELECT id FROM app_users WHERE email='kavitha.r@gauge.io' LIMIT 1), 'EMP004', 'DBX-WFH-1004', '2026-06-25', 'FULL_DAY',    'APPROVED', NULL),
  ((SELECT id FROM app_users WHERE email='priya.k@gauge.io'   LIMIT 1), 'EMP001', 'DBX-WFH-1005', '2026-07-01', 'FULL_DAY',    'APPROVED', NULL);
