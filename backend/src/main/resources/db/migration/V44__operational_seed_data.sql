-- Remove seeded placeholder sync runs; they will be created by real syncs
DELETE FROM darwin_sync_runs;
DELETE FROM jira_sync_runs;

-- ── Developers ──────────────────────────────────────────────────────────────
INSERT INTO developers (name, team, utilization, active_tasks, on_leave, leave_period, initials, avatar_color) VALUES
  ('Arjun Kumar',   'Core',     96, 4, false, null,         'AK', '#6366F1'),
  ('Priya Sharma',  'Core',     82, 3, false, null,         'PS', '#8B5CF6'),
  ('Rajan Menon',   'Platform', 65, 2, false, null,         'RM', '#14B8A6'),
  ('Kavya T.',      'QA',       55, 2, false, null,         'KT', '#F59E0B'),
  ('Dev Lal',       'Platform', 71, 3, false, null,         'DL', '#3B82F6'),
  ('Siddharth M.', 'Core',      0,  0, true,  'Jun 11–18',  'SM', '#94A3B8'),
  ('Anita R.',      'QA',       45, 1, false, null,         'AR', '#10B981'),
  ('Rahul V.',      'Platform', 88, 3, false, null,         'RV', '#EF4444');

-- ── Man-day budgets ──────────────────────────────────────────────────────────
-- project 1 = CRM Core, 2 = Routing Engine, 3 = Collections 2.0, 4 = Analytics 2.0, 5 = Mobile SDK
INSERT INTO man_day_budgets (project_id, purchased_days, period_start, period_end, alert_threshold_pct) VALUES
  (1, 120.00, '2026-01-01', '2026-12-31', 80),
  (2, 180.00, '2026-01-01', '2026-12-31', 80),
  (3,  80.00, '2026-01-01', '2026-12-31', 80),
  (4, 200.00, '2026-01-01', '2026-12-31', 75),
  (5, 150.00, '2026-01-01', '2026-12-31', 80);

-- ── Man-day snapshots: 14 days for each project ──────────────────────────────
-- CRM Core (id=1): 81% burn, 1.7 MD/day, critical — exhausts ~Jun 29
INSERT INTO man_day_snapshots (project_id, snapshot_date, burned_days, remaining_days, burn_rate_per_day, forecast_exhaustion) VALUES
  (1, CURRENT_DATE - 13, 72.60, 47.40, 1.70, CURRENT_DATE + 28),
  (1, CURRENT_DATE - 12, 74.30, 45.70, 1.72, CURRENT_DATE + 27),
  (1, CURRENT_DATE - 11, 76.00, 44.00, 1.70, CURRENT_DATE + 26),
  (1, CURRENT_DATE - 10, 77.70, 42.30, 1.71, CURRENT_DATE + 25),
  (1, CURRENT_DATE -  9, 79.40, 40.60, 1.73, CURRENT_DATE + 24),
  (1, CURRENT_DATE -  8, 81.10, 38.90, 1.70, CURRENT_DATE + 23),
  (1, CURRENT_DATE -  7, 82.80, 37.20, 1.72, CURRENT_DATE + 22),
  (1, CURRENT_DATE -  6, 84.50, 35.50, 1.71, CURRENT_DATE + 21),
  (1, CURRENT_DATE -  5, 86.20, 33.80, 1.73, CURRENT_DATE + 20),
  (1, CURRENT_DATE -  4, 87.90, 32.10, 1.70, CURRENT_DATE + 19),
  (1, CURRENT_DATE -  3, 89.60, 30.40, 1.72, CURRENT_DATE + 18),
  (1, CURRENT_DATE -  2, 91.30, 28.70, 1.71, CURRENT_DATE + 17),
  (1, CURRENT_DATE -  1, 93.00, 27.00, 1.73, CURRENT_DATE + 16),
  (1, CURRENT_DATE,      94.70, 25.30, 1.70, CURRENT_DATE + 15);

-- Routing Engine (id=2): 44% burn, 0.9 MD/day, healthy — exhausts ~Oct 6
INSERT INTO man_day_snapshots (project_id, snapshot_date, burned_days, remaining_days, burn_rate_per_day, forecast_exhaustion) VALUES
  (2, CURRENT_DATE - 13, 67.30, 112.70, 0.90, CURRENT_DATE + 125),
  (2, CURRENT_DATE - 12, 68.20, 111.80, 0.91, CURRENT_DATE + 123),
  (2, CURRENT_DATE - 11, 69.10, 110.90, 0.90, CURRENT_DATE + 123),
  (2, CURRENT_DATE - 10, 70.00, 110.00, 0.89, CURRENT_DATE + 124),
  (2, CURRENT_DATE -  9, 70.90, 109.10, 0.90, CURRENT_DATE + 121),
  (2, CURRENT_DATE -  8, 71.80, 108.20, 0.91, CURRENT_DATE + 119),
  (2, CURRENT_DATE -  7, 72.70, 107.30, 0.90, CURRENT_DATE + 119),
  (2, CURRENT_DATE -  6, 73.60, 106.40, 0.89, CURRENT_DATE + 120),
  (2, CURRENT_DATE -  5, 74.50, 105.50, 0.90, CURRENT_DATE + 117),
  (2, CURRENT_DATE -  4, 75.40, 104.60, 0.91, CURRENT_DATE + 115),
  (2, CURRENT_DATE -  3, 76.30, 103.70, 0.90, CURRENT_DATE + 115),
  (2, CURRENT_DATE -  2, 77.20, 102.80, 0.89, CURRENT_DATE + 116),
  (2, CURRENT_DATE -  1, 78.10, 101.90, 0.90, CURRENT_DATE + 113),
  (2, CURRENT_DATE,      79.00, 101.00, 0.90, CURRENT_DATE + 112);

-- Collections 2.0 (id=3): 92% burn, 2.1 MD/day, critical — exhausts ~Jun 19
INSERT INTO man_day_snapshots (project_id, snapshot_date, burned_days, remaining_days, burn_rate_per_day, forecast_exhaustion) VALUES
  (3, CURRENT_DATE - 13, 47.30, 32.70, 2.10, CURRENT_DATE + 16),
  (3, CURRENT_DATE - 12, 49.40, 30.60, 2.12, CURRENT_DATE + 14),
  (3, CURRENT_DATE - 11, 51.50, 28.50, 2.10, CURRENT_DATE + 14),
  (3, CURRENT_DATE - 10, 53.60, 26.40, 2.11, CURRENT_DATE + 13),
  (3, CURRENT_DATE -  9, 55.70, 24.30, 2.10, CURRENT_DATE + 12),
  (3, CURRENT_DATE -  8, 57.80, 22.20, 2.12, CURRENT_DATE + 10),
  (3, CURRENT_DATE -  7, 59.90, 20.10, 2.10, CURRENT_DATE +  9),
  (3, CURRENT_DATE -  6, 62.00, 18.00, 2.11, CURRENT_DATE +  8),
  (3, CURRENT_DATE -  5, 64.10, 15.90, 2.10, CURRENT_DATE +  8),
  (3, CURRENT_DATE -  4, 66.20, 13.80, 2.12, CURRENT_DATE +  7),
  (3, CURRENT_DATE -  3, 68.30, 11.70, 2.10, CURRENT_DATE +  6),
  (3, CURRENT_DATE -  2, 70.40,  9.60, 2.11, CURRENT_DATE +  5),
  (3, CURRENT_DATE -  1, 72.50,  7.50, 2.10, CURRENT_DATE +  4),
  (3, CURRENT_DATE,      74.60,  5.40, 2.10, CURRENT_DATE +  3);

-- Analytics 2.0 (id=4): 67% burn, 1.4 MD/day, watch — exhausts ~Aug 2
INSERT INTO man_day_snapshots (project_id, snapshot_date, burned_days, remaining_days, burn_rate_per_day, forecast_exhaustion) VALUES
  (4, CURRENT_DATE - 13, 115.40, 84.60, 1.40, CURRENT_DATE + 60),
  (4, CURRENT_DATE - 12, 116.80, 83.20, 1.41, CURRENT_DATE + 59),
  (4, CURRENT_DATE - 11, 118.20, 81.80, 1.40, CURRENT_DATE + 58),
  (4, CURRENT_DATE - 10, 119.60, 80.40, 1.39, CURRENT_DATE + 58),
  (4, CURRENT_DATE -  9, 121.00, 79.00, 1.40, CURRENT_DATE + 56),
  (4, CURRENT_DATE -  8, 122.40, 77.60, 1.41, CURRENT_DATE + 55),
  (4, CURRENT_DATE -  7, 123.80, 76.20, 1.40, CURRENT_DATE + 54),
  (4, CURRENT_DATE -  6, 125.20, 74.80, 1.39, CURRENT_DATE + 54),
  (4, CURRENT_DATE -  5, 126.60, 73.40, 1.40, CURRENT_DATE + 52),
  (4, CURRENT_DATE -  4, 128.00, 72.00, 1.41, CURRENT_DATE + 51),
  (4, CURRENT_DATE -  3, 129.40, 70.60, 1.40, CURRENT_DATE + 50),
  (4, CURRENT_DATE -  2, 130.80, 69.20, 1.39, CURRENT_DATE + 50),
  (4, CURRENT_DATE -  1, 132.20, 67.80, 1.40, CURRENT_DATE + 48),
  (4, CURRENT_DATE,      133.60, 66.40, 1.40, CURRENT_DATE + 47);

-- Mobile SDK (id=5): 58% burn, 1.1 MD/day, watch — exhausts ~Aug 12
INSERT INTO man_day_snapshots (project_id, snapshot_date, burned_days, remaining_days, burn_rate_per_day, forecast_exhaustion) VALUES
  (5, CURRENT_DATE - 13, 72.70, 77.30, 1.10, CURRENT_DATE + 70),
  (5, CURRENT_DATE - 12, 73.80, 76.20, 1.11, CURRENT_DATE + 69),
  (5, CURRENT_DATE - 11, 74.90, 75.10, 1.10, CURRENT_DATE + 68),
  (5, CURRENT_DATE - 10, 76.00, 74.00, 1.09, CURRENT_DATE + 68),
  (5, CURRENT_DATE -  9, 77.10, 72.90, 1.10, CURRENT_DATE + 66),
  (5, CURRENT_DATE -  8, 78.20, 71.80, 1.11, CURRENT_DATE + 65),
  (5, CURRENT_DATE -  7, 79.30, 70.70, 1.10, CURRENT_DATE + 64),
  (5, CURRENT_DATE -  6, 80.40, 69.60, 1.09, CURRENT_DATE + 64),
  (5, CURRENT_DATE -  5, 81.50, 68.50, 1.10, CURRENT_DATE + 62),
  (5, CURRENT_DATE -  4, 82.60, 67.40, 1.11, CURRENT_DATE + 61),
  (5, CURRENT_DATE -  3, 83.70, 66.30, 1.10, CURRENT_DATE + 60),
  (5, CURRENT_DATE -  2, 84.80, 65.20, 1.09, CURRENT_DATE + 60),
  (5, CURRENT_DATE -  1, 85.90, 64.10, 1.10, CURRENT_DATE + 58),
  (5, CURRENT_DATE,      87.00, 63.00, 1.10, CURRENT_DATE + 57);

-- ── Alerts ───────────────────────────────────────────────────────────────────
INSERT INTO alerts (alert_type, severity, client_id, project_id, title, detail, source_agent, status, created_at) VALUES
  ('SLA breached', 'critical',
   (SELECT id FROM clients WHERE code='SG'), (SELECT id FROM projects WHERE name='Collections 2.0'),
   'Collections 2.0 — P0 SLA breached, 3h overdue',
   'Customer-impacting payment failure. No resolution in 7h. Assignee last updated 6h ago.',
   'DeliveryIntelligenceAgent', 'OPEN', NOW() - INTERVAL '2 hours'),

  ('Budget risk', 'critical',
   (SELECT id FROM clients WHERE code='SG'), (SELECT id FROM projects WHERE name='Collections 2.0'),
   'Sigma Telecom — budget exhausts in 3 days',
   'At 2.1 MD/day burn rate, 5.4 MD remaining. Contract runs to Dec 31.',
   'ManDayForecastAgent', 'OPEN', NOW() - INTERVAL '1 hour'),

  ('Budget risk', 'critical',
   (SELECT id FROM clients WHERE code='NX'), (SELECT id FROM projects WHERE name='CRM Core'),
   'CRM Core budget at 79% — exhausts in 15 days',
   'Burn rate accelerated 29% over 2 weeks due to parallel dev. 25 MD remaining.',
   'ManDayForecastAgent', 'OPEN', NOW() - INTERVAL '4 hours'),

  ('Hold aging', 'risk',
   (SELECT id FROM clients WHERE code='NX'), (SELECT id FROM projects WHERE name='CRM Core'),
   'CR on Hold 18 days — no owner, all milestones TBC',
   'Last follow-up May 29. Jul 5 cutoff at risk if not unblocked today.',
   'DeliveryIntelligenceAgent', 'OPEN', NOW() - INTERVAL '3 hours'),

  ('UAT blocker', 'risk',
   (SELECT id FROM clients WHERE code='MB'), (SELECT id FROM projects WHERE name='Analytics 2.0'),
   '3 UAT blockers before Meridian Jul 5 release',
   'Unresolved UAT bugs. Retest window closes Jun 20.',
   'DeliveryIntelligenceAgent', 'OPEN', NOW() - INTERVAL '5 hours'),

  ('Capacity overload', 'risk',
   (SELECT id FROM clients WHERE code='NX'), (SELECT id FROM projects WHERE name='CRM Core'),
   'Arjun Kumar at 96% — 4 active CRs',
   '2 CRs have milestone targets in next 7 days. Recommend rebalancing workload.',
   'DeliveryIntelligenceAgent', 'OPEN', NOW() - INTERVAL '6 hours'),

  ('Budget risk', 'risk',
   (SELECT id FROM clients WHERE code='MB'), (SELECT id FROM projects WHERE name='Analytics 2.0'),
   'Analytics 2.0 burn at 67% — watch threshold',
   'Burn rate steady at 1.4 MD/day. Jul 5 release adds risk if scope not locked.',
   'ManDayForecastAgent', 'ACKNOWLEDGED', NOW() - INTERVAL '8 hours'),

  ('Stale sync', 'info',
   NULL, NULL,
   'Jira delta sync delay — last sync 48m ago',
   'Delta sync expected every 10 min. Webhook may have missed events.',
   'SyncMonitor', 'OPEN', NOW() - INTERVAL '48 minutes');
