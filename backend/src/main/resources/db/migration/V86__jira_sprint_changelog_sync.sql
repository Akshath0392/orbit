-- AM widget-parity Wave 3 / F3 (docs/plan/orbitter-am-widget-parity-plan.md):
-- sprint modelling + issue changelog ledger for velocity, cycle time,
-- reopen counting and the predictability pillar.

-- Sprints, upserted from the issue Sprint custom-field payload (no Board API).
CREATE TABLE IF NOT EXISTS sprints (
    id                    BIGSERIAL PRIMARY KEY,
    jira_sprint_id        BIGINT NOT NULL UNIQUE,
    board_id              BIGINT,
    name                  VARCHAR(255),
    state                 VARCHAR(16),          -- future | active | closed
    start_date            TIMESTAMP,
    end_date              TIMESTAMP,
    complete_date         TIMESTAMP,
    goal                  TEXT,
    committed_snapshot_at TIMESTAMP,            -- set when the activation snapshot ran; NULL → committed SP is approximate
    last_synced_at        TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sprints_state ON sprints(state);
CREATE INDEX IF NOT EXISTS idx_sprints_start ON sprints(start_date);

-- Sprint membership with add/remove times (from Sprint-field changelog diffs).
CREATE TABLE IF NOT EXISTS sprint_issues (
    id                     BIGSERIAL PRIMARY KEY,
    sprint_id              BIGINT NOT NULL REFERENCES sprints(id) ON DELETE CASCADE,
    issue_id               BIGINT NOT NULL REFERENCES jira_issues(id) ON DELETE CASCADE,
    added_at               TIMESTAMP,
    removed_at             TIMESTAMP,
    committed              BOOLEAN,             -- member at sprint start (+15 min grace), D4
    committed_story_points NUMERIC(6,2),
    UNIQUE(sprint_id, issue_id)
);
CREATE INDEX IF NOT EXISTS idx_sprint_issues_issue ON sprint_issues(issue_id);

-- issue_transitions (V21, orphaned until now) becomes a generic field-change
-- ledger shared by webhook + backfill. Derived values (first_in_progress_at,
-- reopen_count) are always RECOMPUTED from this ledger, never incremented.
ALTER TABLE issue_transitions ADD COLUMN IF NOT EXISTS field_type   VARCHAR(20) NOT NULL DEFAULT 'status'; -- status | sprint | story_points
ALTER TABLE issue_transitions ADD COLUMN IF NOT EXISTS from_value   TEXT;
ALTER TABLE issue_transitions ADD COLUMN IF NOT EXISTS to_value     TEXT;
ALTER TABLE issue_transitions ADD COLUMN IF NOT EXISTS changelog_id VARCHAR(40);
-- webhook + backfill dedup: one row per Jira changelog entry per field
CREATE UNIQUE INDEX IF NOT EXISTS uq_issue_transitions_changelog
    ON issue_transitions(issue_id, changelog_id, field_type) WHERE changelog_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_issue_transitions_field
    ON issue_transitions(issue_id, field_type, transitioned_at);

-- Derived/backfill columns on issues
ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS first_in_progress_at TIMESTAMP;   -- first transition into an in-progress category
ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS changelog_synced_at  TIMESTAMP;   -- backfill cursor; NULL = never backfilled
CREATE INDEX IF NOT EXISTS idx_jira_issues_changelog_pending
    ON jira_issues(id) WHERE changelog_synced_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_jira_issues_resolved_at ON jira_issues(resolved_at);
