ALTER TABLE projects
  ADD COLUMN jira_project_keys VARCHAR(255),
  ADD COLUMN jira_jql_override TEXT,
  ADD COLUMN jira_cr_filter    TEXT,
  ADD COLUMN jira_bug_filter   TEXT;

-- Seed default Jira config for existing projects
UPDATE projects SET
  jira_project_keys = 'NX',
  jira_jql_override = 'project = "NX" AND updated >= -24h ORDER BY updated DESC',
  jira_cr_filter    = 'issuetype = "Change Request"',
  jira_bug_filter   = 'issuetype = Bug AND environment = Production'
WHERE name = 'CRM Core';

UPDATE projects SET
  jira_project_keys = 'NX',
  jira_jql_override = 'project = "NX" AND component = "Routing" AND updated >= -24h',
  jira_cr_filter    = 'issuetype = "Change Request"',
  jira_bug_filter   = 'issuetype = Bug AND environment = Production'
WHERE name = 'Routing Engine';

UPDATE projects SET
  jira_project_keys = 'SG,COLL',
  jira_jql_override = 'project IN ("SG","COLL") AND updated >= -24h',
  jira_cr_filter    = 'issuetype = "CR"',
  jira_bug_filter   = 'issuetype in (Bug,"Production Bug") AND priority in (P0,P1,P2)'
WHERE name = 'Collections 2.0';

UPDATE projects SET
  jira_project_keys = 'MB',
  jira_jql_override = 'project = "MB" AND updated >= -24h',
  jira_cr_filter    = 'issuetype = "Change Request"',
  jira_bug_filter   = 'issuetype = Bug'
WHERE name = 'Analytics 2.0';

UPDATE projects SET
  jira_project_keys = 'AF',
  jira_jql_override = 'project = "AF" AND updated >= -24h',
  jira_cr_filter    = 'issuetype = Story',
  jira_bug_filter   = 'issuetype = Bug AND priority in (P0,P1)'
WHERE name = 'Mobile SDK';
