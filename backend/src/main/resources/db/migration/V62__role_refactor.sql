-- Rename roles to match persona model
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS can_edit_budget BOOLEAN DEFAULT FALSE;

UPDATE app_users SET role = 'PM',          can_edit_budget = TRUE  WHERE role = 'HEAD_PJM';
UPDATE app_users SET role = 'PM',          can_edit_budget = FALSE WHERE role = 'PJM';
UPDATE app_users SET role = 'ENGINEERING'                          WHERE role = 'ENG_MANAGER';

-- Add CSM + REVENUE seed users (if admin exists, keep it)
INSERT INTO app_users (name, email, role, password, initials, avatar_color, can_edit_budget)
  SELECT 'CSM User', 'csm@orbit.io', 'CSM', password, 'CS', '#b83280', FALSE
  FROM app_users WHERE email='admin@orbit.io' LIMIT 1
  ON CONFLICT (email) DO NOTHING;

INSERT INTO app_users (name, email, role, password, initials, avatar_color, can_edit_budget)
  SELECT 'Revenue User', 'revenue@orbit.io', 'REVENUE', password, 'RV', '#2563eb', FALSE
  FROM app_users WHERE email='admin@orbit.io' LIMIT 1
  ON CONFLICT (email) DO NOTHING;

-- Update role_screen_config
DELETE FROM role_screen_config WHERE role_name IN ('HEAD_PJM','PJM','ENG_MANAGER');

INSERT INTO role_screen_config (role_name, display_name, screen_ids) VALUES
  ('PM',         'Project Management',    'radar,cockpit,cr,bugs,uat,mandays,alerts,reports,capacity,clients,integrations,audit,agent-builder')
  ON CONFLICT (role_name) DO UPDATE SET display_name=EXCLUDED.display_name, screen_ids=EXCLUDED.screen_ids;

INSERT INTO role_screen_config (role_name, display_name, screen_ids) VALUES
  ('ENGINEERING','Engineering',           'radar,capacity,mandays,agent-builder')
  ON CONFLICT (role_name) DO UPDATE SET display_name=EXCLUDED.display_name, screen_ids=EXCLUDED.screen_ids;

INSERT INTO role_screen_config (role_name, display_name, screen_ids) VALUES
  ('CSM',        'Account Management',    'radar,clients,alerts,reports')
  ON CONFLICT (role_name) DO UPDATE SET display_name=EXCLUDED.display_name, screen_ids=EXCLUDED.screen_ids;

INSERT INTO role_screen_config (role_name, display_name, screen_ids) VALUES
  ('REVENUE',    'Revenue',               'radar,mandays,reports,capacity')
  ON CONFLICT (role_name) DO UPDATE SET display_name=EXCLUDED.display_name, screen_ids=EXCLUDED.screen_ids;

UPDATE role_screen_config
  SET screen_ids = 'radar,cockpit,cr,bugs,uat,mandays,alerts,reports,capacity,clients,integrations,audit,admin,agent-builder'
  WHERE role_name = 'ADMIN';

UPDATE role_screen_config SET screen_ids = 'radar' WHERE role_name = 'LEADERSHIP';

-- Client enrichment fields
ALTER TABLE clients ADD COLUMN IF NOT EXISTS csat DECIMAL(3,1);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS renewal_date DATE;

-- Developer availability fields
ALTER TABLE developers ADD COLUMN IF NOT EXISTS available_from_date DATE;
ALTER TABLE developers ADD COLUMN IF NOT EXISTS last_dev_end_date DATE;
ALTER TABLE developers ADD COLUMN IF NOT EXISTS next_project_id BIGINT REFERENCES projects(id);

-- Seed CSAT on existing clients
UPDATE clients SET csat = 7.8 + (id % 3) * 0.3 WHERE csat IS NULL;
