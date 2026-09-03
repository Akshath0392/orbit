# Orbit Roadmap

Where the project is heading. Nothing here is a commitment — issues and PRs
shape priority.

## Shipped in v0.1.0

- Jira Cloud sync (full + delta with a standing 30-min cron), live run
  observability (progress, project scope, run history), webhook ingest
- Configurable delivery lifecycle: stage catalog, per-issue-type stage
  mapping with a canonical type vocabulary (CR / PROD_BUG / UAT_BUG / TASK)
- Dashboards: portfolio radar, account detail, CR workbench, bug triage,
  client master — with deep-linkable drill-downs and per-role chart
  configuration
- Health & risk scoring (bulk-context, cached), man-day budget burn tracking,
  SLA targets and breach tracking
- AI copilot + agents (bring-your-own Anthropic/OpenAI key; no-ops when
  unconfigured), Slack integration, HITL gates
- Pluggable HRMS connector factory (Darwinbox reference implementation) for
  attendance/leave signals
- RBAC (7 roles), JWT auth (RS256), optional Google SSO, feature flags

## Under consideration

- **Historical trends engine** — periodic issue snapshots to power
  trend/velocity dashboards (week-over-week movement, aging curves).
  Largest open design; will land behind its own schema when a generic
  Trends dashboard is designed.
- **More HRMS providers** — the connector SPI is stable; BambooHR / Workday /
  Keka providers are natural contributions.
- **Jira Data Center support** — current sync targets Jira Cloud REST v3.
- **Pluggable issue trackers** — Linear / GitHub Issues behind the same
  lifecycle mapping layer.
- **Deeper chart configurability** — more chart primitives adopting the
  per-role chart-config vocabulary shipped in v0.1.0.
- **Mobile-friendly shell** — the responsive layout exists; a PWA wrapper is
  unexplored.

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md). Good first areas: HRMS providers,
chart adoption of `useChartConfig()`, and docs.
