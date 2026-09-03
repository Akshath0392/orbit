-- Seed demo wins + governance meetings so the new RadarPage sections and
-- AccountDetailPage have visible content out-of-the-box. No-op when projects
-- table is empty (fresh install or production).

INSERT INTO project_wins (project_id, win, recognised_on, source, created_by)
SELECT p.id,
       'Client appreciated end-to-end UAT sign-off velocity',
       CURRENT_DATE - INTERVAL '5 days',
       'Status call',
       'system'
FROM projects p
WHERE NOT EXISTS (SELECT 1 FROM project_wins w WHERE w.project_id = p.id)
LIMIT 5;

INSERT INTO project_wins (project_id, win, recognised_on, source, created_by)
SELECT p.id,
       'Zero P0 escapes in last release cycle',
       CURRENT_DATE - INTERVAL '12 days',
       'Steering committee',
       'system'
FROM projects p
WHERE NOT EXISTS (
    SELECT 1 FROM project_wins w
    WHERE w.project_id = p.id AND w.win LIKE 'Zero P0%'
)
LIMIT 5;

INSERT INTO governance_meetings (portfolio_id, cadence, title, last_held, next_due, owner, status)
SELECT pf.id, 'Weekly', 'Portfolio steering — ' || pf.name,
       CURRENT_DATE - INTERVAL '3 days',
       CURRENT_DATE + INTERVAL '4 days',
       'PMO',
       'On track'
FROM portfolios pf
WHERE NOT EXISTS (
    SELECT 1 FROM governance_meetings g WHERE g.portfolio_id = pf.id
);

INSERT INTO governance_meetings (project_id, cadence, title, last_held, next_due, owner, status)
SELECT p.id, 'Fortnightly', 'Client business review',
       CURRENT_DATE - INTERVAL '8 days',
       CURRENT_DATE + INTERVAL '6 days',
       'Account manager',
       'On track'
FROM projects p
WHERE NOT EXISTS (
    SELECT 1 FROM governance_meetings g WHERE g.project_id = p.id
);
