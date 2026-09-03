CREATE TABLE slack_config (
  id              BIGSERIAL PRIMARY KEY,
  workspace_name  VARCHAR(100),
  bot_token       TEXT NOT NULL,
  signing_secret  TEXT,
  default_channel VARCHAR(100),
  enabled         BOOLEAN DEFAULT true,
  created_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE slack_project_channels (
  project_id   BIGINT REFERENCES projects(id) ON DELETE CASCADE,
  channel_id   VARCHAR(100) NOT NULL,
  channel_name VARCHAR(100),
  PRIMARY KEY (project_id)
);
