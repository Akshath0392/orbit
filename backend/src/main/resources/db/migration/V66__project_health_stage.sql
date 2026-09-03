-- ── Project life-stage fields ────────────────────────────────────────────────
ALTER TABLE projects ADD COLUMN IF NOT EXISTS go_live_date   DATE;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS health_stage   VARCHAR(20);  -- NULL = auto-inferred

-- ── Health profile weights ────────────────────────────────────────────────────
-- Each row defines how much a metric contributes to health deduction for a stage.
-- weight (0-100): contribution to penalty; sensitivity controls normalization curve.
-- Weights within a stage don't need to sum to 100 — they cap independently.
CREATE TABLE health_profile_weights (
  id          BIGSERIAL PRIMARY KEY,
  stage       VARCHAR(20)  NOT NULL,  -- PRE_LAUNCH | HYPERCARE | STEADY_STATE | AT_RISK
  metric      VARCHAR(50)  NOT NULL,  -- prod_bug_p0 | prod_bug_p1 | sla_breach | cr_on_hold_pct | uat_bug_count | manday_burn_risk
  weight      INTEGER      NOT NULL DEFAULT 0,   -- max deduction this metric can contribute
  sensitivity DECIMAL(4,2) NOT NULL DEFAULT 1.0, -- how fast metric reaches full deduction
  UNIQUE(stage, metric)
);

-- PRE_LAUNCH: not live yet — prod bugs irrelevant, delivery pipeline is what matters
INSERT INTO health_profile_weights (stage, metric, weight, sensitivity) VALUES
  ('PRE_LAUNCH', 'prod_bug_p0',       0,  1.0),
  ('PRE_LAUNCH', 'prod_bug_p1',       0,  1.0),
  ('PRE_LAUNCH', 'sla_breach',        0,  1.0),
  ('PRE_LAUNCH', 'cr_on_hold_pct',   30,  1.5),
  ('PRE_LAUNCH', 'uat_bug_count',    35,  1.0),
  ('PRE_LAUNCH', 'manday_burn_risk', 35,  1.0);

-- HYPERCARE: first 90 days post go-live — production stability dominates
INSERT INTO health_profile_weights (stage, metric, weight, sensitivity) VALUES
  ('HYPERCARE', 'prod_bug_p0',       35,  0.5),
  ('HYPERCARE', 'prod_bug_p1',       20,  0.3),
  ('HYPERCARE', 'sla_breach',        30,  0.5),
  ('HYPERCARE', 'cr_on_hold_pct',   10,  1.0),
  ('HYPERCARE', 'uat_bug_count',     0,  1.0),
  ('HYPERCARE', 'manday_burn_risk',   5,  1.0);

-- STEADY_STATE: 90+ days — balanced across support quality and delivery velocity
INSERT INTO health_profile_weights (stage, metric, weight, sensitivity) VALUES
  ('STEADY_STATE', 'prod_bug_p0',       20,  0.5),
  ('STEADY_STATE', 'prod_bug_p1',       15,  0.3),
  ('STEADY_STATE', 'sla_breach',        25,  0.5),
  ('STEADY_STATE', 'cr_on_hold_pct',   25,  1.5),
  ('STEADY_STATE', 'uat_bug_count',     5,  1.0),
  ('STEADY_STATE', 'manday_burn_risk', 10,  1.0);

-- AT_RISK: manual flag — all signals elevated
INSERT INTO health_profile_weights (stage, metric, weight, sensitivity) VALUES
  ('AT_RISK', 'prod_bug_p0',       25,  0.5),
  ('AT_RISK', 'prod_bug_p1',       15,  0.3),
  ('AT_RISK', 'sla_breach',        20,  0.5),
  ('AT_RISK', 'cr_on_hold_pct',   20,  1.5),
  ('AT_RISK', 'uat_bug_count',    10,  1.0),
  ('AT_RISK', 'manday_burn_risk', 10,  1.0);
