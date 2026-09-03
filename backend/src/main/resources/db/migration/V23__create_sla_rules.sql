CREATE TABLE sla_rules (
  id               BIGSERIAL PRIMARY KEY,
  client_id        BIGINT REFERENCES clients(id),
  severity         VARCHAR(10),
  response_hours   DECIMAL(5,1),
  resolution_hours DECIMAL(5,1),
  include_weekends BOOLEAN DEFAULT FALSE
);
