-- Per the product rule: bugs tracked under projects are UAT bugs by default.
-- Only bugs explicitly tagged in Jira as "Production Bug" / "Production Defect"
-- should land in PROD_BUG. The earlier mapIssueType() mapped Jira "Bug" → PROD_BUG;
-- this migration reclassifies that legacy data.
--
-- Heuristic: anything currently PROD_BUG whose Jira status doesn't reflect a true
-- production incident (i.e. originated from a project's Jira "Bug" type) becomes
-- UAT_BUG. We keep PROD_BUG only for issues whose jira_status indicates a real
-- production incident workflow (e.g. "Production", "Hotfix", "Outage").

UPDATE jira_issues
SET issue_type = 'UAT_BUG'
WHERE issue_type = 'PROD_BUG'
  AND (jira_status IS NULL
       OR LOWER(jira_status) NOT LIKE '%production%'
       AND LOWER(jira_status) NOT LIKE '%hotfix%'
       AND LOWER(jira_status) NOT LIKE '%outage%'
       AND LOWER(jira_status) NOT LIKE '%sev-1%'
       AND LOWER(jira_status) NOT LIKE '%sev1%');
