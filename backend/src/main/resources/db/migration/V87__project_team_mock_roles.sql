-- Mock Teams tab carries six internal roles (PM, Solution Manager,
-- Engineering Manager, Tech Lead, QA Lead, Support Manager) — add the three
-- missing ones. Nullable; existing rows unaffected.
ALTER TABLE project_team ADD COLUMN IF NOT EXISTS internal_tech_lead VARCHAR(120);
ALTER TABLE project_team ADD COLUMN IF NOT EXISTS internal_qa_lead VARCHAR(120);
ALTER TABLE project_team ADD COLUMN IF NOT EXISTS internal_support_mgr VARCHAR(120);
