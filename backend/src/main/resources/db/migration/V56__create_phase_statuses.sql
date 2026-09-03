CREATE TABLE phase_statuses (
  id               BIGSERIAL    PRIMARY KEY,
  project_id       BIGINT       REFERENCES projects(id) ON DELETE CASCADE,
  phase            VARCHAR(20)  NOT NULL,
  start_date       DATE,
  end_date         DATE,
  assignee_email   VARCHAR(255),
  assignee_name    VARCHAR(255),
  status           VARCHAR(30)  DEFAULT 'NOT_STARTED',
  delay_note       TEXT,
  jira_issue_key   VARCHAR(50),
  last_notified_t2 DATE,
  last_notified_t1 DATE,
  dday_notified    BOOLEAN      DEFAULT false,
  updated_at       TIMESTAMP    DEFAULT NOW(),
  UNIQUE (project_id, phase)
);

CREATE INDEX idx_ps_project  ON phase_statuses(project_id);
CREATE INDEX idx_ps_end_date ON phase_statuses(end_date);
CREATE INDEX idx_ps_status   ON phase_statuses(status);
