-- V80 — Shared Jira prod-bug routing.
--
-- One shared Jira project carries production bugs for every
-- client, distinguished only by a custom field (customfield_11683). This
-- migration equips the model to fan bugs out to the right client at ingest,
-- without changing the primary jira_issues → project relationship. See
-- docs/plan/prod-bug-routing-plan.md.

-- 1a) Data cleanup: normalise codes (trim + uppercase) before enforcing
--     uniqueness. Case-insensitive-per-row matching happens through
--     ClientRepository.findByCodeIgnoreCase; storing uppercase in the
--     column keeps the raw data legible in psql sessions.
UPDATE clients
   SET code = UPPER(TRIM(code))
 WHERE code IS NOT NULL
   AND code <> ''
   AND code <> UPPER(TRIM(code));

-- 1b) Dedup: existing seed data may already carry the same code on
--     multiple rows (case-variants collapsed above, or hand-entered dupes).
--     Keep the lowest-id row for each duplicate group and BLANK the rest —
--     the partial unique index below skips empty strings, and application
--     code (ClientController, ProdBugRoutingController) treats blank as
--     "no code set". Admins can re-enter the correct code for the blanked
--     rows through the Prod-bug routing UI. Set to '' rather than NULL
--     because `clients.code` has an app-level NOT NULL constraint on some
--     deployments.
UPDATE clients c
   SET code = ''
  FROM (
    SELECT id, code,
           ROW_NUMBER() OVER (PARTITION BY code ORDER BY id) AS rn
      FROM clients
     WHERE code IS NOT NULL
       AND code <> ''
  ) dup
 WHERE dup.id = c.id
   AND dup.rn > 1;

-- 1c) Client codes must be unique when present. Nulls stay allowed so existing
--     clients without a code aren't blocked.
CREATE UNIQUE INDEX uq_clients_code
  ON clients (code)
  WHERE code IS NOT NULL AND code <> '';

-- 2) Mark Orbit projects that pool prod bugs across clients + record which
--    Jira custom field carries the client code. Per-project so multi-region
--    Jiras with different field IDs can coexist.
ALTER TABLE projects
  ADD COLUMN is_shared_prod_bugs BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN client_code_field   VARCHAR(64);

-- 3) Quarantine: one row per Jira issue whose client_code was missing/unknown
--    at sync time. jira_key is unique so re-sync updates in place (bumps
--    last_seen_at) rather than piling up duplicates. Bugs stay linked to their
--    JiraIssue row via jira_issue_id so we can jump back to the source.
CREATE TABLE prod_bug_quarantine (
  id               BIGSERIAL PRIMARY KEY,
  jira_issue_id    BIGINT REFERENCES jira_issues(id) ON DELETE CASCADE,
  jira_key         VARCHAR(64) UNIQUE NOT NULL,
  raw_client_code  VARCHAR(64),                      -- NULL when the field was blank
  reason           VARCHAR(32) NOT NULL,             -- MISSING_CODE | UNKNOWN_CODE
  seen_at          TIMESTAMP NOT NULL DEFAULT now(),
  last_seen_at     TIMESTAMP NOT NULL DEFAULT now(), -- bumped on every re-sync while stuck
  resolved_at      TIMESTAMP,
  resolved_by      VARCHAR(255),
  resolution_note  TEXT
);

CREATE INDEX idx_quarantine_open
  ON prod_bug_quarantine (last_seen_at DESC)
  WHERE resolved_at IS NULL;
