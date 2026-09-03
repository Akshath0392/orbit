CREATE INDEX idx_adl_date    ON agent_decision_log(decided_at DESC);
CREATE INDEX idx_rpt_status  ON generated_reports(status);
CREATE INDEX idx_al_created  ON alerts(created_at DESC);
CREATE INDEX idx_ji_project  ON jira_issues(project_id);
