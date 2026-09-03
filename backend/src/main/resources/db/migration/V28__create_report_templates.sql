CREATE TABLE report_templates (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  type        VARCHAR(50),
  description TEXT,
  active      BOOLEAN DEFAULT TRUE
);
