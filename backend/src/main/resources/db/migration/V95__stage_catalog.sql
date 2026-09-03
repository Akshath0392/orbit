-- Stage catalog: single source of truth for delivery stage names, ordering and
-- colour category. Replaces the frontend-hardcoded AKKI_STAGES list; membership
-- of /cr/stages stays mapping-derived, order/category resolve from here.

CREATE TABLE IF NOT EXISTS stages (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(64) NOT NULL UNIQUE,
    display_order INT         NOT NULL DEFAULT 50,
    category      VARCHAR(20) NOT NULL DEFAULT 'in-progress',
    updated_by    VARCHAR(120),
    updated_at    TIMESTAMP   NOT NULL DEFAULT now()
);

-- Legacy hardcoded list (frontend AKKI_STAGES) so a fresh install starts sane.
INSERT INTO stages (name, display_order, category) VALUES
    ('BRD awaited',    10, 'backlog'),
    ('FSD awaited',    20, 'backlog'),
    ('Effort pending', 30, 'backlog'),
    ('In dev',         40, 'in-progress'),
    ('In QA',          50, 'qa'),
    ('In UAT',         60, 'uat'),
    ('Ready for prod', 70, 'ready'),
    ('Released',       80, 'released'),
    ('Hold',           90, 'blocked'),
    ('Closed',        100, 'closed')
ON CONFLICT (name) DO NOTHING;

-- Stages already used by mappings, carrying over their V72 order/category.
INSERT INTO stages (name, display_order, category)
SELECT DISTINCT ON (gauge_stage)
       gauge_stage, COALESCE(display_order, 50), COALESCE(category, 'in-progress')
FROM   lifecycle_mappings
WHERE  gauge_stage IS NOT NULL AND btrim(gauge_stage) <> ''
ORDER  BY gauge_stage, display_order NULLS LAST
ON CONFLICT (name) DO NOTHING;

-- Stages present on issues but never mapped (auto-discover default branch
-- copies the raw Jira status) — no existing reference may be orphaned.
INSERT INTO stages (name, display_order, category)
SELECT DISTINCT left(lifecycle_stage, 64), 50, 'in-progress'
FROM   jira_issues
WHERE  lifecycle_stage IS NOT NULL AND btrim(lifecycle_stage) <> ''
ON CONFLICT (name) DO NOTHING;
