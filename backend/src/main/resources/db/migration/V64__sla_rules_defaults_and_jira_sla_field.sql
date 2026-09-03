-- Default SLA rules (client_id = NULL means global default)
-- responseHours = "At risk" threshold; resolutionHours = "Breached" threshold
INSERT INTO sla_rules (client_id, severity, response_hours, resolution_hours, include_weekends) VALUES
  (NULL, 'P0',   2.0,   4.0, TRUE),
  (NULL, 'P1',  16.0,  24.0, FALSE),
  (NULL, 'P2',  48.0,  72.0, FALSE),
  (NULL, 'P3', 120.0, 168.0, FALSE)
ON CONFLICT DO NOTHING;

-- Add optional Jira SLA custom field name (e.g. customfield_10020 for JSM)
ALTER TABLE jira_config ADD COLUMN IF NOT EXISTS sla_field VARCHAR(60);
