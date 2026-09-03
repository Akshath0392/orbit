CREATE TABLE clients (
  id                      BIGSERIAL PRIMARY KEY,
  name                    VARCHAR(100) NOT NULL UNIQUE,
  code                    VARCHAR(20)  NOT NULL UNIQUE,
  health_green_threshold  INTEGER DEFAULT 80,
  health_amber_threshold  INTEGER DEFAULT 60,
  contact_name            VARCHAR(100),
  active                  BOOLEAN DEFAULT TRUE
);
