CREATE TABLE man_day_budgets (
  id                  BIGSERIAL PRIMARY KEY,
  project_id          BIGINT REFERENCES projects(id) UNIQUE,
  purchased_days      DECIMAL(10,2),
  period_start        DATE,
  period_end          DATE,
  daily_rate_hours    DECIMAL(5,2) DEFAULT 8.0,
  alert_threshold_pct INTEGER DEFAULT 80
);
