CREATE TABLE projects (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  client_id   BIGINT REFERENCES clients(id),
  active      BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_projects_client ON projects(client_id);
