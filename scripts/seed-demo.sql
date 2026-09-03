-- Orbit neutral demo dataset — OPT-IN, never loaded automatically.
--
-- Load AFTER the backend has booted once (Flyway must have created the schema):
--   psql "$SPRING_DATASOURCE_URL" -f scripts/seed-demo.sql
--   # or locally: psql -U orbit -d orbit -f scripts/seed-demo.sql
-- Or set SEED_DEMO_DATA=true in .env and run scripts/start-all.sh, which
-- applies this file once the backend is up.
--
-- Idempotent: every insert is guarded (ON CONFLICT DO NOTHING / NOT EXISTS),
-- so re-running is a no-op. Fictional companies only — safe for screenshots.
-- Intentionally small: 3 clients, 1 portfolio, 3 projects. Everything else
-- (users, budgets, Jira config, HRMS config) is left for the app/admin UI.

INSERT INTO clients (name, code, health_green_threshold, health_amber_threshold, contact_name, active)
VALUES
  ('Acme Corp', 'ACME', 80, 60, 'Jordan Reyes', TRUE),
  ('Globex',    'GLBX', 80, 60, 'Sam Whitfield', TRUE),
  ('Initech',   'INIT', 75, 55, 'Alex Nakamura', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO portfolios (name, description, active)
SELECT 'Demo Portfolio', 'Sample portfolio for evaluation and screenshots', TRUE
WHERE NOT EXISTS (SELECT 1 FROM portfolios WHERE name = 'Demo Portfolio');

INSERT INTO portfolio_clients (portfolio_id, client_id)
SELECT pf.id, c.id
FROM portfolios pf
JOIN clients c ON c.code IN ('ACME', 'GLBX', 'INIT') AND c.active
WHERE pf.name = 'Demo Portfolio'
ON CONFLICT DO NOTHING;

INSERT INTO projects (name, client_id, portfolio_id, active)
SELECT v.name, c.id, (SELECT id FROM portfolios WHERE name = 'Demo Portfolio' LIMIT 1), TRUE
FROM (VALUES
  ('Acme CRM Rollout',     'ACME'),
  ('Globex Analytics',     'GLBX'),
  ('Initech Mobile Suite', 'INIT')
) AS v(name, code)
JOIN clients c ON c.code = v.code AND c.active
WHERE NOT EXISTS (SELECT 1 FROM projects p WHERE p.name = v.name);
