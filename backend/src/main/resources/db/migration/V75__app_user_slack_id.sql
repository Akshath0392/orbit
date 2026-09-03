ALTER TABLE app_users ADD COLUMN IF NOT EXISTS slack_user_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_app_users_slack_user_id ON app_users(slack_user_id) WHERE slack_user_id IS NOT NULL;
