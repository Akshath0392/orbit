-- Phase 4: account metadata fields, wins register, governance meetings

ALTER TABLE projects ADD COLUMN IF NOT EXISTS account_type        VARCHAR(50);   -- Strategic | Growth | Steady | Watch
ALTER TABLE projects ADD COLUMN IF NOT EXISTS revenue_exposure    NUMERIC(14,2); -- annual contract value in INR
ALTER TABLE projects ADD COLUMN IF NOT EXISTS contract_end_date   DATE;

CREATE TABLE project_wins (
  id           BIGSERIAL PRIMARY KEY,
  project_id   BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  win          TEXT   NOT NULL,
  recognised_on DATE,
  source       VARCHAR(50),         -- Client email · Status call · NPS
  created_at   TIMESTAMP DEFAULT NOW(),
  created_by   VARCHAR(150)
);
CREATE INDEX idx_project_wins_project ON project_wins(project_id);

CREATE TABLE governance_meetings (
  id           BIGSERIAL PRIMARY KEY,
  project_id   BIGINT REFERENCES projects(id) ON DELETE CASCADE,    -- null = portfolio-level
  portfolio_id BIGINT REFERENCES portfolios(id) ON DELETE CASCADE,
  cadence      VARCHAR(30)    NOT NULL,   -- Weekly | Fortnightly | Monthly | Quarterly | Adhoc
  title        VARCHAR(200)   NOT NULL,
  last_held    DATE,
  next_due     DATE,
  owner        VARCHAR(150),
  status       VARCHAR(20),               -- On track | Slipping | Missed
  notes        TEXT
);
CREATE INDEX idx_gov_meetings_project   ON governance_meetings(project_id);
CREATE INDEX idx_gov_meetings_portfolio ON governance_meetings(portfolio_id);
CREATE INDEX idx_gov_meetings_next_due  ON governance_meetings(next_due);
