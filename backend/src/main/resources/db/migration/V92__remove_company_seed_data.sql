-- Remove the company-specific seed rows that survived V45.
--
-- Chain analysis (fresh DB, V16→V91):
--   * V39/V40/V44 sample data (clients NX/SG/MB/AF/PR, their portfolios,
--     projects, developers, budgets, snapshots, alerts) was fully deleted by
--     V45__remove_all_seed_data. Any client/project row present at this point
--     was therefore entered by a user and must not be touched here.
--   * V62's csm@orbit.io / revenue@orbit.io demo accounts are product-neutral
--     and were password-locked by V89 — left in place.
--   * V71__seed_wins_governance_demo is the only seed that re-inserted rows
--     after V45: demo wins (created_by='system') and governance meetings
--     attached to whatever projects/portfolios existed at migration time.
--     Those rows are removed below, matched on their exact seeded shape so
--     user-entered rows are never hit.

DELETE FROM project_wins
 WHERE created_by = 'system'
   AND (
        (win = 'Client appreciated end-to-end UAT sign-off velocity' AND source = 'Status call')
     OR (win = 'Zero P0 escapes in last release cycle'               AND source = 'Steering committee')
   );

DELETE FROM governance_meetings
 WHERE (cadence = 'Weekly'      AND owner = 'PMO'             AND portfolio_id IS NOT NULL
        AND title LIKE 'Portfolio steering — %')
    OR (cadence = 'Fortnightly' AND owner = 'Account manager' AND project_id IS NOT NULL
        AND title = 'Client business review');

-- Defensive: the V40 sample clients cannot exist post-V45 unless a database
-- skipped it; remove them only when they are demonstrably the untouched seed —
-- exact name+code pair AND nothing referencing them (so the delete can never
-- fail an FK check or take user data with it).
DELETE FROM clients c
 WHERE (c.name, c.code) IN (('Nexus Corp','NX'), ('Sigma Telecom','SG'),
                            ('Meridian','MB'), ('Apex Fintech','AF'), ('Polaris','PR'))
   AND NOT EXISTS (SELECT 1 FROM projects            r WHERE r.client_id = c.id)
   AND NOT EXISTS (SELECT 1 FROM portfolio_clients   r WHERE r.client_id = c.id)
   AND NOT EXISTS (SELECT 1 FROM jira_issues         r WHERE r.client_id = c.id)
   AND NOT EXISTS (SELECT 1 FROM sla_rules           r WHERE r.client_id = c.id)
   AND NOT EXISTS (SELECT 1 FROM alerts              r WHERE r.client_id = c.id)
   AND NOT EXISTS (SELECT 1 FROM generated_reports   r WHERE r.client_id = c.id)
   AND NOT EXISTS (SELECT 1 FROM report_schedules    r WHERE r.client_id = c.id)
   AND NOT EXISTS (SELECT 1 FROM client_dependencies r WHERE r.client_id = c.id);
