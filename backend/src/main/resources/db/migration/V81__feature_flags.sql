-- Feature flags for controlled rollout (piloting). Deliberately independent of
-- role_screen_config: audience is ALL (everyone), PILOT (only emails listed in
-- pilot_emails), or NONE (hidden). ADMINs always see flagged features so they
-- can verify before widening the audience.
-- Key convention: screen.<navId> gates a route + sidebar item;
--                 section.<page>.<name> gates a component inside a page.
-- Unknown keys default to visible in the frontend, so a row is only needed for
-- features being held back.
CREATE TABLE IF NOT EXISTS feature_flags (
    id           BIGSERIAL PRIMARY KEY,
    flag_key     VARCHAR(120) NOT NULL UNIQUE,
    description  VARCHAR(500),
    audience     VARCHAR(16)  NOT NULL DEFAULT 'ALL',
    pilot_emails JSONB        NOT NULL DEFAULT '[]',
    updated_by   VARCHAR(255),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Expose the flag-management screen to admins in the sidebar.
UPDATE role_screen_config
  SET screen_ids = screen_ids || ',flags'
  WHERE role_name = 'ADMIN' AND screen_ids NOT LIKE '%flags%';
