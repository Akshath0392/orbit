CREATE TABLE generated_reports (
  id               BIGSERIAL PRIMARY KEY,
  type             VARCHAR(50),
  client_id        BIGINT REFERENCES clients(id),
  project_id       BIGINT REFERENCES projects(id),
  status           VARCHAR(20) DEFAULT 'GENERATING',
  generated_by     VARCHAR(100),
  manual_notes     TEXT,
  client_safe      BOOLEAN DEFAULT TRUE,
  content_json     JSONB,
  generated_at     TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_rpt_client ON generated_reports(client_id);
