-- Per-role chart customization for dashboard charts.
-- chart_config stores PRESENTATION PREFERENCE only — {chartType: line|bar|stacked,
-- breakdownChartType: bar|line, palette: classic|vibrant, runtimeToggle: on|off};
-- absent keys mean defaults. Rollout state stays in the section.charts.config
-- feature flag, never in this column.
ALTER TABLE role_screen_config ADD COLUMN IF NOT EXISTS chart_config JSONB;

-- Register the capability flag so it is visible/manageable on the flags admin
-- page. Seeded at ALL: the feature ships enabled; flip audience to NONE to
-- hold it back.
INSERT INTO feature_flags(flag_key, description, audience)
SELECT 'section.charts.config',
       'Per-role chart look & feel (chart type + palette) plus the in-page chart-type switcher grant',
       'ALL'
WHERE NOT EXISTS (SELECT 1 FROM feature_flags f WHERE f.flag_key = 'section.charts.config');
