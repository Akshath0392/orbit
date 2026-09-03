CREATE TABLE man_day_snapshots (
  id                  BIGSERIAL PRIMARY KEY,
  project_id          BIGINT REFERENCES projects(id),
  snapshot_date       DATE,
  burned_days         DECIMAL(10,2),
  remaining_days      DECIMAL(10,2),
  burn_rate_per_day   DECIMAL(6,3),
  forecast_exhaustion DATE
);

CREATE INDEX idx_mds_project_date ON man_day_snapshots(project_id, snapshot_date DESC);
