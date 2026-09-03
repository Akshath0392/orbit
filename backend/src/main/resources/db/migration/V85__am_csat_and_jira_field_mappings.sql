-- AM widget-parity Wave 2 (docs/plan/orbitter-am-widget-parity-plan.md).

-- F1: admin-entered CSAT per client (interim source until a survey feed exists;
-- a future feed just replaces the writer) + engagement score. Existing seeded
-- clients.csat stays (deprecated). Clients table is populated → nullable adds.
ALTER TABLE clients ADD COLUMN IF NOT EXISTS csat_launch      NUMERIC(3,1);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS csat_bau         NUMERIC(3,1);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS engagement_score INT;

-- F2: Jira custom-field mappings (same pattern as sla_field) + the issue
-- columns they populate. Sprint columns hold the CURRENT sprint from the
-- issue's Sprint field; full sprint/changelog modelling lands in Wave 3.
ALTER TABLE jira_config ADD COLUMN IF NOT EXISTS story_points_field VARCHAR(60);
ALTER TABLE jira_config ADD COLUMN IF NOT EXISTS sprint_field       VARCHAR(60);
ALTER TABLE jira_config ADD COLUMN IF NOT EXISTS sm_field           VARCHAR(60);
ALTER TABLE jira_config ADD COLUMN IF NOT EXISTS pjm_field          VARCHAR(60);

ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS story_points        NUMERIC(6,2);
ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS sm_owner            VARCHAR(255);
ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS pjm_owner           VARCHAR(255);
ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS current_sprint_id   BIGINT;
ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS current_sprint_name VARCHAR(255);
