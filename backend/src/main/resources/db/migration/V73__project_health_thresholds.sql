-- Per-project health threshold overrides. Defaults match the global
-- thresholds in application.yml (akki.client.health-default-*).

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS health_green_threshold INT DEFAULT 80,
    ADD COLUMN IF NOT EXISTS health_amber_threshold INT DEFAULT 60;

UPDATE projects SET health_green_threshold = 80 WHERE health_green_threshold IS NULL;
UPDATE projects SET health_amber_threshold = 60 WHERE health_amber_threshold IS NULL;
