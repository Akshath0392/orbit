CREATE TABLE escalation_config (
  id                    BIGSERIAL    PRIMARY KEY,
  role                  VARCHAR(50)  NOT NULL,
  phase                 VARCHAR(20),
  phase_spoc_email      VARCHAR(255),
  phase_spoc_name       VARCHAR(255),
  delivery_spoc_enabled BOOLEAN      DEFAULT true,
  re_escalation_hours   INT          DEFAULT 24,
  updated_at            TIMESTAMP    DEFAULT NOW()
);

INSERT INTO escalation_config (role, phase, delivery_spoc_enabled, re_escalation_hours) VALUES
  ('DEVELOPER',        'DEV',  true, 24),
  ('TECH_LEAD',        'DEV',  true, 24),
  ('TECH_LEAD',        'PROD', true, 12),
  ('QA_LEAD',          'QA',   true, 24),
  ('PROJECT_MANAGER',  'UAT',  true, 24),
  ('SOLUTION_MANAGER', 'FSD',  true, 24);

CREATE TABLE global_spoc_config (
  id            BIGSERIAL   PRIMARY KEY,
  spoc_type     VARCHAR(30) NOT NULL UNIQUE,
  email         VARCHAR(255),
  name          VARCHAR(255),
  slack_user_id VARCHAR(50),
  updated_at    TIMESTAMP   DEFAULT NOW()
);

INSERT INTO global_spoc_config (spoc_type) VALUES
  ('DELIVERY_SPOC'),
  ('SOLUTIONS_SPOC'),
  ('ENG_MANAGER');
