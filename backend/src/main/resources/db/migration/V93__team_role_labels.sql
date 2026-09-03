-- Display labels for the project_team internal-role columns. The columns keep
-- their keys; installs rename what each role is called (Admin console) instead
-- of inheriting one company's org chart.
CREATE TABLE team_role_labels (
  role_key TEXT PRIMARY KEY,
  label    TEXT NOT NULL
);

INSERT INTO team_role_labels(role_key, label) VALUES
  ('internal_pm',          'Project Manager'),
  ('internal_am',          'Account Manager'),
  ('internal_sol',         'Delivery Manager'),
  ('internal_em',          'Engineering Manager'),
  ('internal_tech_lead',   'Tech Lead'),
  ('internal_qa_lead',     'QA Lead'),
  ('internal_support_mgr', 'Support Manager');
