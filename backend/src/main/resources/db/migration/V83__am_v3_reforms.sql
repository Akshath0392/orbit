-- AM dashboard V3 reforms (docs/plan/orbitter-am-v3-reforms-plan.md).
-- Client master page delivery-health metric cards whose data feeds don't exist
-- yet ship dark: flags at NONE (admins still see them). Delete each flag when
-- its feed lands (Phase C: sprint/changelog sync · Phase D: CSAT/deploy events).
INSERT INTO feature_flags(flag_key, description, audience)
SELECT t.k, t.d, 'NONE' FROM (VALUES
    ('section.client.dh.cycle',           'Cycle Time — awaiting status-changelog sync (In Progress timestamp)'),
    ('section.client.dh.leakage',         'Defect Leakage — awaiting found-in-environment field mapping'),
    ('section.client.dh.reopened',        'Reopened Issues — awaiting status-changelog sync (Done→reopen transitions)'),
    ('section.client.dh.cfr',             'Change Failure Rate — awaiting deployment-event feed (DORA)'),
    ('section.client.dh.rework',          'Rework % — awaiting story points + post-Done worklog feed'),
    ('section.client.dh.commitment',      'Sprint Commitment Reliability — awaiting Jira sprint sync (Phase 3)'),
    ('section.client.dh.spillover',       'Spillover % — awaiting Jira sprint sync (Phase 3)'),
    ('section.client.dh.scope-change',    'Scope Change % — awaiting Jira sprint sync (Phase 3)'),
    ('section.client.dh.release-success', 'Release Success Rate — awaiting release/rollback event feed'),
    ('section.client.milestones',         'Milestones auto-derived from Sprint Scope — awaiting per-account Jira sprint filter'),
    ('section.client.sprint-scope',       'Sprint Scope (phase-grouped tracker) — awaiting per-account Jira sprint filter')
) AS t(k, d)
WHERE NOT EXISTS (SELECT 1 FROM feature_flags f WHERE f.flag_key = t.k);
