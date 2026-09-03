-- Remove all seed/demo data in FK-safe order.
-- role_screen_config and flyway_schema_history are intentionally preserved.

DELETE FROM agent_decision_log;
DELETE FROM agent_project_summaries;
DELETE FROM report_schedules;
DELETE FROM generated_reports;
DELETE FROM report_templates;
DELETE FROM alert_actions;
DELETE FROM alerts;
DELETE FROM issue_notes;
DELETE FROM issue_embeddings;
DELETE FROM issue_milestones;
DELETE FROM issue_transitions;
DELETE FROM uat_sign_offs;
DELETE FROM uat_cycles;
DELETE FROM jira_issues;
DELETE FROM jira_webhook_events;
DELETE FROM man_day_snapshots;
DELETE FROM man_day_budgets;
DELETE FROM leave_records;
DELETE FROM darwin_sync_runs;
DELETE FROM jira_sync_runs;
DELETE FROM developers;
DELETE FROM sla_rules;
DELETE FROM lifecycle_mappings;
DELETE FROM client_dependencies;
UPDATE projects SET portfolio_id = NULL;
DELETE FROM projects;
DELETE FROM portfolios;
DELETE FROM clients;
DELETE FROM app_users;
