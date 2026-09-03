-- AM widget-parity Wave 1 (docs/plan/orbitter-am-widget-parity-plan.md).

-- Single-row AM settings: delivery-health pillar weights (admin-configurable,
-- mock ⚙ modal default 40/35/25) and the external adoption-dashboard deep link
-- (F4 — filled in Wave 2, column reserved here so the row shape is stable).
CREATE TABLE IF NOT EXISTS am_settings (
    id                BIGINT PRIMARY KEY,
    dh_speed_weight   INT NOT NULL DEFAULT 40,
    dh_quality_weight INT NOT NULL DEFAULT 35,
    dh_pred_weight    INT NOT NULL DEFAULT 25,
    adoption_url      VARCHAR(512),
    updated_by        VARCHAR(255),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);
INSERT INTO am_settings(id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- V82 seeded the mock-vocabulary stage targets; the live Jira taxonomy uses
-- different stage names, so on real data nothing was SLA-tracked (audit
-- finding, W7/W8). Same mock buckets (Intake/Solutioning 30 · Approval 15 ·
-- Dev 45 · UAT 21 · Prod readiness 15) mapped onto the observed stages.
-- Hold / On Hold / Rejected stay untracked by design.
INSERT INTO stage_sla_targets(stage, target_days) VALUES
    ('BRD awaited',                      30),
    ('CR Created',                       30),
    ('Request Created',                  30),
    ('PM Review',                        30),
    ('Solutioning',                      30),
    ('New',                              30),
    ('Finance approval Pending',         15),
    ('Client Approval',                  15),
    ('In dev',                           45),
    ('Development',                      45),
    ('In QA',                            45),
    ('QA Review in progress - Staging',  45),
    ('QA Review in progress - Pre-prod', 45),
    ('QA Review in progress - POD',      45),
    ('Ready for QA Review - Staging',    45),
    ('Ready for QA Review - Pre-prod',   45),
    ('Ready for QA Review - POD',        45),
    ('Ready For Staging',                45),
    ('Ready For Pre-prod',               45),
    ('UAT in progress',                  21),
    ('Fixed',                            15),
    ('Ready for prod',                   15),
    ('Ready For Production',             15)
ON CONFLICT (stage) DO NOTHING;
