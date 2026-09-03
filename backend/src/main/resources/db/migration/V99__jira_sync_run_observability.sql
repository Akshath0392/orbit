-- Jira sync run observability: per-run attribution, live progress and project
-- scope. No FK on project_id — runs must survive project deletion.
-- triggered_by holds the triggering user's email; 'system' when
-- unauthenticated, 'scheduler' for the standing delta cron.
-- total_expected is the approximate-count of the run's JQL scope taken at
-- start; processed_so_far advances per fetched page so the runs page can show
-- synced-vs-pending while a sync is still running. project_scope = ordered
-- comma-separated project names stamped at run start; current_project advances
-- as the sequential loop moves (cleared on Success, kept on Failed so the row
-- shows where the run died). All NULL on historical rows.
ALTER TABLE jira_sync_runs ADD COLUMN IF NOT EXISTS project_id       BIGINT;
ALTER TABLE jira_sync_runs ADD COLUMN IF NOT EXISTS triggered_by     VARCHAR(100);
ALTER TABLE jira_sync_runs ADD COLUMN IF NOT EXISTS total_expected   INTEGER;
ALTER TABLE jira_sync_runs ADD COLUMN IF NOT EXISTS processed_so_far INTEGER;
ALTER TABLE jira_sync_runs ADD COLUMN IF NOT EXISTS project_scope    TEXT;
ALTER TABLE jira_sync_runs ADD COLUMN IF NOT EXISTS current_project  VARCHAR(200);

-- Date-filtered stats + paginated runs/webhook listings
CREATE INDEX IF NOT EXISTS idx_jira_sync_runs_type_started  ON jira_sync_runs(sync_type, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_jira_webhook_events_received ON jira_webhook_events(received_at DESC);
