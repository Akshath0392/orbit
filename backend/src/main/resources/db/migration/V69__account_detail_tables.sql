-- Account-detail support tables: team contacts, risk register, release calendar

ALTER TABLE projects ADD COLUMN IF NOT EXISTS ops_model VARCHAR(20);  -- launch | bau | launch+bau

CREATE TABLE project_team (
  project_id        BIGINT PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
  internal_pm       VARCHAR(150),
  internal_am       VARCHAR(150),
  internal_em       VARCHAR(150),
  internal_sol      VARCHAR(150),
  client_sponsor    VARCHAR(150),
  client_tech_spoc  VARCHAR(150),
  client_biz_spoc   VARCHAR(150),
  client_pm         VARCHAR(150),
  updated_at        TIMESTAMP DEFAULT NOW(),
  updated_by        VARCHAR(150)
);

CREATE TABLE project_risks (
  id           BIGSERIAL PRIMARY KEY,
  project_id   BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  jira_ticket  VARCHAR(50),
  risk         TEXT   NOT NULL,
  received_on  DATE,
  rag          VARCHAR(10),       -- Green | Amber | Red
  action_end   DATE,
  action_owner VARCHAR(150),
  source       VARCHAR(50),       -- Client email · Status call · Escalation
  created_at   TIMESTAMP DEFAULT NOW(),
  created_by   VARCHAR(150)
);
CREATE INDEX idx_project_risks_project ON project_risks(project_id);

CREATE TABLE project_releases (
  id            BIGSERIAL PRIMARY KEY,
  project_id    BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  release_date  DATE NOT NULL,
  release_type  VARCHAR(20),       -- launch | bau | support
  label         VARCHAR(150),
  rag           VARCHAR(10)
);
CREATE INDEX idx_project_releases_project ON project_releases(project_id);
CREATE INDEX idx_project_releases_date    ON project_releases(release_date);
