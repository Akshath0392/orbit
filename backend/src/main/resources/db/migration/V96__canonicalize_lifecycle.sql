-- Lifecycle canonicalization, two repairs in one migration.
--
-- Part A — terminal Jira statuses land in the Closed stage. Rejected/Cancelled
-- usually have no lifecycle_mappings rows, so the sync leaked the raw Jira
-- status into lifecycle_stage and dead issues kept counting as in-flight;
-- 'Released to Production' mapped to 'Released' — product decision: released
-- work is closed work. Status list is hardcoded (incl. US-spelling Canceled)
-- so it never depends on admin-entered mapping rows being present.
--
-- Part B — lifecycle_mappings.issue_type must use the sync vocabulary
-- (CR, PROD_BUG, UAT_BUG, TASK, OTHER) or the wildcard ALL. The admin UI used
-- to save its dropdown labels verbatim ('Bug', 'UAT Bug', 'Task', 'All'); the
-- stage-map lookup key is an exact string match, so those rows only ever acted
-- through the bare-status fallback and could never override per type.
--
-- Idempotent by construction.

-- A1. Live table: what the sync would now produce with the mappings in place.
UPDATE jira_issues
SET lifecycle_stage = 'Closed'
WHERE jira_status IN ('Rejected', 'Cancelled', 'Canceled', 'Released to Production')
  AND (lifecycle_stage IS NULL OR lifecycle_stage <> 'Closed');

-- A2. Closed rows with NULL resolved_at drop out of every closed-in-period
-- widget. Jira only sends resolutiondate when a resolution was set, which
-- cancellations often lack. Use the moment the issue entered its terminal
-- status per the transitions ledger (field_type = 'status', V86); fall back to
-- updated_at when the ledger predates sync.
UPDATE jira_issues j
SET resolved_at = COALESCE(
      (SELECT max(t.transitioned_at) FROM issue_transitions t
        WHERE t.issue_id = j.id AND t.field_type = 'status'),
      j.updated_at)
WHERE j.jira_status IN ('Rejected', 'Cancelled', 'Canceled', 'Released to Production')
  AND j.resolved_at IS NULL;

-- B1. Drop legacy rows shadowed by an existing canonical twin (relabelling
-- them would collide with the (jira_status, issue_type) unique constraint).
DELETE FROM lifecycle_mappings lm
USING (VALUES
        ('bug',            'PROD_BUG'),
        ('prod bug',       'PROD_BUG'),
        ('prod_bug',       'PROD_BUG'),
        ('production bug', 'PROD_BUG'),
        ('uat bug',        'UAT_BUG'),
        ('uat_bug',        'UAT_BUG'),
        ('uat defect',     'UAT_BUG'),
        ('task',           'TASK'),
        ('cr',             'CR'),
        ('all',            'ALL'),
        ('other',          'OTHER')
      ) AS map(legacy, canonical),
      lifecycle_mappings twin
WHERE lower(btrim(lm.issue_type)) = map.legacy
  AND lm.issue_type <> map.canonical
  AND twin.issue_type = map.canonical
  AND twin.jira_status = lm.jira_status;

-- B2. Relabel the remainder.
UPDATE lifecycle_mappings lm
SET issue_type = map.canonical
FROM (VALUES
        ('bug',            'PROD_BUG'),
        ('prod bug',       'PROD_BUG'),
        ('prod_bug',       'PROD_BUG'),
        ('production bug', 'PROD_BUG'),
        ('uat bug',        'UAT_BUG'),
        ('uat_bug',        'UAT_BUG'),
        ('uat defect',     'UAT_BUG'),
        ('task',           'TASK'),
        ('cr',             'CR'),
        ('all',            'ALL'),
        ('other',          'OTHER')
      ) AS map(legacy, canonical)
WHERE lower(btrim(lm.issue_type)) = map.legacy
  AND lm.issue_type <> map.canonical;
