-- Magic-link tokens issued by /orbit-link slash command.
-- User clicks confirm link from email → POST /api/v1/slack/link/confirm → app_users.slack_user_id set.
CREATE TABLE IF NOT EXISTS slack_magic_link (
    id              BIGSERIAL PRIMARY KEY,
    token           VARCHAR(96) NOT NULL UNIQUE,
    slack_user_id   VARCHAR(64) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP NOT NULL,
    consumed_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_slack_magic_link_token       ON slack_magic_link (token);
CREATE INDEX IF NOT EXISTS idx_slack_magic_link_slack_user  ON slack_magic_link (slack_user_id);
