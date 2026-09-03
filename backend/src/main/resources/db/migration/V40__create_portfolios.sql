CREATE TABLE portfolios (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  client_id   BIGINT REFERENCES clients(id),
  description VARCHAR(255),
  active      BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_portfolios_client ON portfolios(client_id);

ALTER TABLE projects ADD COLUMN portfolio_id BIGINT REFERENCES portfolios(id);
CREATE INDEX idx_projects_portfolio ON projects(portfolio_id);

-- Seed clients
INSERT INTO clients (name, code, health_green_threshold, health_amber_threshold, contact_name, active) VALUES
  ('Nexus Corp',    'NX', 80, 60, 'Sanjay Mehta',   TRUE),
  ('Sigma Telecom', 'SG', 80, 60, 'Pooja Rajan',    TRUE),
  ('Meridian',      'MB', 75, 55, 'Aruna Thomas',   TRUE),
  ('Apex Fintech',  'AF', 80, 60, 'Kiran Desai',    TRUE),
  ('Polaris',       'PR', 80, 60, 'Nidhi Sharma',   TRUE);

-- Seed portfolios (one per client for now; admin can add more)
INSERT INTO portfolios (name, client_id, description, active) VALUES
  ('CRM Platform',    (SELECT id FROM clients WHERE code='NX'), 'Core CRM and routing products', TRUE),
  ('Collections',     (SELECT id FROM clients WHERE code='SG'), 'Debt collection and analytics', TRUE),
  ('Analytics',       (SELECT id FROM clients WHERE code='MB'), 'Analytics and mobile suite',   TRUE),
  ('Mobile Banking',  (SELECT id FROM clients WHERE code='AF'), 'Mobile SDK and payments',      TRUE),
  ('Infrastructure',  (SELECT id FROM clients WHERE code='PR'), 'Platform infrastructure',      TRUE);

-- Seed projects (assigned to portfolios)
INSERT INTO projects (name, client_id, portfolio_id, active) VALUES
  ('CRM Core',       (SELECT id FROM clients WHERE code='NX'), (SELECT id FROM portfolios WHERE name='CRM Platform'),   TRUE),
  ('Routing Engine', (SELECT id FROM clients WHERE code='NX'), (SELECT id FROM portfolios WHERE name='CRM Platform'),   TRUE),
  ('Collections 2.0',(SELECT id FROM clients WHERE code='SG'), (SELECT id FROM portfolios WHERE name='Collections'),    TRUE),
  ('Analytics 2.0',  (SELECT id FROM clients WHERE code='MB'), (SELECT id FROM portfolios WHERE name='Analytics'),      TRUE),
  ('Mobile SDK',     (SELECT id FROM clients WHERE code='AF'), (SELECT id FROM portfolios WHERE name='Mobile Banking'),  TRUE);
