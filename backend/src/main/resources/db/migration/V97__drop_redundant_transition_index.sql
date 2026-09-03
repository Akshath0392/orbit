-- Index tuning on the fast-growing transitions ledger.
-- idx_tr_issue (V21, issue_transitions(issue_id)) is a pure prefix of
-- idx_issue_transitions_field (issue_id, field_type, transitioned_at) from V86
-- — every issue_id lookup is served by the composite.
DROP INDEX IF EXISTS idx_tr_issue;
