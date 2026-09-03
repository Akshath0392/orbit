-- Configurable export templates (export_templates — a legacy
-- report_templates table with a different shape already exists). sections = ordered JSON array of
-- {key, enabled}; the report renderer honors order + enabled without redeploy.
CREATE TABLE IF NOT EXISTS export_templates (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    scope       VARCHAR(8)   NOT NULL DEFAULT 'acct',   -- acct | pod
    sections    TEXT         NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT false,
    updated_by  VARCHAR(255),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_export_templates_default_scope ON export_templates(scope) WHERE is_default;

-- Mock account Delivery Report (orbit-preview-1.html reportAcctBody)
INSERT INTO export_templates (name, scope, sections, is_default, updated_by)
SELECT 'Account Delivery Report', 'acct',
  '[{"key":"executiveSummary","enabled":true},{"key":"keyMetrics","enabled":true},{"key":"productionIssues","enabled":true},{"key":"milestones","enabled":true},{"key":"commercials","enabled":true},{"key":"riskRegister","enabled":true}]',
  true, 'seed'
WHERE NOT EXISTS (SELECT 1 FROM export_templates WHERE scope = 'acct' AND is_default);
