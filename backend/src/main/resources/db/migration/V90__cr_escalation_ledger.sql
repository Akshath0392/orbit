-- SLA-breach escalation loop — dedup ledger.
-- One row per CR that the scheduled sweep has proposed an escalation for, so a
-- CR is never re-nagged every sweep. The sweep skips any CR whose last_proposed_at
-- is within the cooldown window. Keyed by the Jira issue key (stable across syncs).
-- Recompute-from-state, never incremented → replay-safe.
CREATE TABLE cr_escalation (
    issue_key        VARCHAR(64) PRIMARY KEY,
    last_proposed_at TIMESTAMP   NOT NULL,
    last_outcome     VARCHAR(32),          -- null until HITL settles (APPROVED|REJECTED|EDITED)
    decision_log_id  BIGINT                -- FK-by-convention to agent_decision_log.id (Wave 3)
);

CREATE INDEX idx_cr_escalation_proposed_at ON cr_escalation (last_proposed_at);
