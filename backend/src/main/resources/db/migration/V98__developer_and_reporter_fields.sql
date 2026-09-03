-- Developer + Reporter sync. Developer identity comes from a Jira "Developer"
-- user-picker custom field (same jira_config mapping pattern as sm_field);
-- reporter comes from Jira's standard reporter object. All nullable — a blank
-- mapping keeps the feature dark, and rows synced before this migration stay
-- NULL (no cliff in history).

ALTER TABLE jira_config ADD COLUMN IF NOT EXISTS developer_field VARCHAR(60);

ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS developer_name  VARCHAR(255);
ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS reporter_name   VARCHAR(255);
ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS reporter_email  VARCHAR(255);
