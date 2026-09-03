-- Account Management dashboard (docs/plan/orbitter-am-dashboard-plan.md).
-- Stage-aging SLA targets keyed on lifecycle gauge_stage values. Stages without
-- a row are not SLA-tracked (mock parity: Client Hold / Unstaged untracked).
CREATE TABLE IF NOT EXISTS stage_sla_targets (
    id          BIGSERIAL PRIMARY KEY,
    stage       VARCHAR(64) NOT NULL UNIQUE,
    target_days INT         NOT NULL,
    updated_by  VARCHAR(255),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Seeds map the approved mock targets (Intake 30 · Finance 15 · Dev 45 · UAT 21 ·
-- Prod Readiness 15) onto Orbit's stage vocabulary. Calibrate before demo.
INSERT INTO stage_sla_targets(stage, target_days) VALUES
    ('Received',             30),
    ('Validated',            30),
    ('Business Solutioning', 30),
    ('FSD Approval',         15),
    ('Approval',             15),
    ('In Progress',          45),
    ('UAT Released',         21),
    ('Customer Validation',  21),
    ('Ready for Production', 15)
ON CONFLICT (stage) DO NOTHING;

-- Account Management role: workspace screens only, no admin.
INSERT INTO role_screen_config(role_name, screen_ids, display_name)
SELECT 'AM', 'radar,cr,bugs,alerts,reports,clients', 'Account Management'
WHERE NOT EXISTS (SELECT 1 FROM role_screen_config WHERE role_name = 'AM');

-- Hold back AM sections whose data source does not exist yet (unknown key = visible,
-- so only the not-ready sections get rows). Delete the flag when the source lands.
INSERT INTO feature_flags(flag_key, description, audience)
SELECT t.k, t.d, 'NONE' FROM (VALUES
    ('section.radar.am.csat',      'CSAT tiles + drill — awaiting CSAT survey source decision'),
    ('section.radar.am.velocity',  'Velocity (SP committed vs delivered) — awaiting Jira sprint sync (Phase 3)'),
    ('section.radar.am.owners-sm', 'Solutioning Manager view — awaiting SM field mapping from Jira'),
    ('section.radar.am.adoption',  'Adoption deep link — awaiting dashboard URL')
) AS t(k, d)
WHERE NOT EXISTS (SELECT 1 FROM feature_flags f WHERE f.flag_key = t.k);
