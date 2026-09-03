-- Role screen access config (makes frontend RBAC DB-driven)
CREATE TABLE role_screen_config (
  id          BIGSERIAL PRIMARY KEY,
  role_name   VARCHAR(50) NOT NULL UNIQUE,
  screen_ids  TEXT        NOT NULL,  -- comma-separated screen IDs
  display_name VARCHAR(100)
);

INSERT INTO role_screen_config (role_name, display_name, screen_ids) VALUES
('ADMIN',       'Admin',           'radar,cockpit,cr,bugs,uat,mandays,alerts,reports,capacity,clients,jira,darwin,audit,admin'),
('HEAD_PJM',    'Head of PJM',     'radar,cockpit,cr,bugs,uat,mandays,alerts,reports,capacity,clients,jira,darwin,audit'),
('PJM',         'PJM',             'cockpit,cr,bugs,uat,mandays,alerts,reports,capacity,clients,jira'),
('LEADERSHIP',  'Leadership',      'radar'),
('ENG_MANAGER', 'Eng. Manager',    'capacity,mandays');

-- Jira sync run history table (replaces JiraSyncController mocks)
CREATE TABLE jira_sync_runs (
  id             BIGSERIAL PRIMARY KEY,
  sync_type      VARCHAR(20)  NOT NULL,
  status         VARCHAR(20)  NOT NULL,
  issues_processed INTEGER    DEFAULT 0,
  duration_ms    INTEGER,
  error_message  TEXT,
  started_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
  completed_at   TIMESTAMP
);

CREATE INDEX idx_jira_sync_started ON jira_sync_runs(started_at DESC);

-- Seed some realistic jira sync history
INSERT INTO jira_sync_runs (sync_type, status, issues_processed, duration_ms, started_at, completed_at) VALUES
('Delta',   'Success', 12,  4200,  NOW() - INTERVAL '15 minutes', NOW() - INTERVAL '15 minutes' + INTERVAL '4 seconds'),
('Delta',   'Success', 3,   2100,  NOW() - INTERVAL '25 minutes', NOW() - INTERVAL '25 minutes' + INTERVAL '2 seconds'),
('Delta',   'Success', 0,   1800,  NOW() - INTERVAL '35 minutes', NOW() - INTERVAL '35 minutes' + INTERVAL '2 seconds'),
('Full',    'Success', 847, 42000, NOW() - INTERVAL '1 hour',     NOW() - INTERVAL '1 hour' + INTERVAL '42 seconds'),
('Webhook', 'Success', 1,   300,   NOW() - INTERVAL '70 minutes', NOW() - INTERVAL '70 minutes' + INTERVAL '1 second'),
('Delta',   'Failed',  0,   NULL,  NOW() - INTERVAL '80 minutes', NOW() - INTERVAL '80 minutes' + INTERVAL '3 seconds');
