---
name: orbit
description: >
  High-level navigation map + locked rules for Claude sessions working on Orbit.
  Architecture, schema, and screen catalogue live in design/hld.md / design/lld.md — read those
  before writing schema, API, or service code.
version: 5.0
date: 2026-09-03
---

# Orbit — Claude Session Skill

Keep this short — it's a navigation map and a list of locked rules. Everything else lives in [`hld.md`](design/hld.md) / [`lld.md`](design/lld.md).

## What Orbit is

AI-native delivery intelligence platform — self-hosted, no public endpoints, no client access.

Built on top of a Java/Postgres bandwidth-tracking tool by adding Jira lifecycle tracking, a pluggable HRMS connector (Darwinbox ships as the reference implementation), per-project health scoring, HITL-gated AI agents, Slack bidirectional integration, and a React frontend.

Two personas: **PM** ("what needs my attention right now?" → Alerts + delivery boards) and **Leadership** ("which projects will miss commitments?" → Portfolio Radar, read-only).

## Stack (locked — do not substitute)

```
Backend     Spring Boot 3.2 · Java 25 · Spring Data JPA · Spring Security (JWT RS256) · Spring AI 1.x
Frontend    React 18 · TypeScript · Vite · Zustand · React Query · SockJS+STOMP · custom DS (no MUI/Tailwind/shadcn)
Database    PostgreSQL 16 + pgvector · Redis 7 (Lettuce) · Flyway V16–V100
AI/ML       Claude (default) or OpenAI via AI_PROVIDER · text-embedding-3-small · Prophet sidecar (stubbed) · BERT triage (stubbed)
Build       Maven · Docker Compose (dev) · Kubernetes (prod)
```

## Repo map

```
backend/src/main/java/com/orbit/
  controller/                 REST endpoints (admin + app)
  connector/hrms/             HrmsConnector SPI + HrmsConnectorFactory · darwinbox/ reference provider
  service/agent/              AgentRuntime · NativeAgentRegistry · HitlApprovalService · AgentInvocationService
  service/snapshot/           SnapshotService · RadarSnapshotAgent · SnapshotRendererClient (Mock|Http) · SnapshotStorageService · SnapshotJwtService · SnapshotWatchdog
  service/sync/               JiraSyncService · ProdBugRoutingService · ProdBugBackfillService · BertTriageService
  service/slack/              IntentResolver · SlackToolExecutor · SlackIdentity · SlackConversationStore
  integration/slack/          SlackInteractionRouter · SlackHitlBridge · SlackResponseRenderer · SlackClient · SnapshotSlackHandler
  service/ai/                 AiGateway (Anthropic + OpenAI providers)
  domain/                     JPA entities
  security/                   JwtService · SecurityConfig · SlackSignatureVerifier
  src/main/resources/db/migration/  Flyway V16–V100
frontend/src/
  features/<screen>/          one folder per screen, kebab-case
  features/admin/agents/      AgentBuilderPage (Agents tab)
  features/admin/agent-logs/  AgentLogsPage (read-only audit view)
  features/admin/flags/       FeatureFlagsPage — Features Control Center (controlled release, lld §5.20)
  app/featureFlags.tsx        useFlag/<Feature> gates · TopBar+Shell consume screen.<navId> flags
  layout/                     TopBar · Shell · nav.tsx (PRODUCT_NAV, ADMIN_NAV, RBAC×flag gating)
  design/                     atoms (Card, Btn, Badge, Pagination, …) + composites (PageHeader, StatGrid,
                              FilterBar, TableWrap, StatusPill) + useBreakpoint — see design/frontend-component-strategy.md
services/
  snapshot-sidecar/           Node 20 + Playwright Express. POST /render → {png,pdf,renderMs}. Activated by snapshot.renderer=http (see lld §15.9).
docs/
  design/
    hld.md  lld.md            canonical architecture & schema
    frontend-component-strategy.md  composite catalog · responsive rules · flag-gating conventions
  plan/                       implementation plans (rule 13)
  CLAUDE.md                   this file
```

## Locked conventions (do not relitigate)

1. **No external component library.** Custom design system only — no MUI, Tailwind, shadcn, Chakra. Tokens live in `frontend/src/design/`; reference colours via the `C` object — never hardcoded hex.
2. **Every list endpoint paginates.** API returns `Page<T>` with `?page=&size=`; every table renders `<Pagination/>`. No infinite scroll, no "show all".
3. **HITL is mandatory for escalation.** `escalation.require-hitl: true` may never be relaxed. `send_notification` is never called outside `EscalationAgent`'s approval gate.
4. **REJECTED proposals require a reason.** `outcome_note TEXT` is mandatory; enforced server-side.
5. **Every alert has `source_agent`.** Always populate on INSERT.
6. **Man-day budget edit requires the `canEditBudget` JWT claim** (a `PM` with `can_edit_budget = true`, or `ADMIN`). Enforced server-side in `ManDayController` — not just hidden in UI.
7. **Per-client health thresholds.** Read `client.healthGreenThreshold` / `healthAmberThreshold` from the API; never hardcode `>= 80 ? green : …`.
8. **Slack bot token + signing secret live in DB** (`slack_config`), configured via Admin → Integrations → Slack. Never put them in `application.yml` or env vars.
   - **Exception — snapshot JWT:** the snapshot agent mints a 5-min token with `scope=snapshot:read`. `JwtFilter` elevates that token to `ROLE_ADMIN` **on GET requests only**, and only for the Radar data surface (`SNAPSHOT_ELEVATED_PREFIXES` — deliberately excludes `/admin/**` and `/hrms/**` PII endpoints), so any user can render any radar lens. Never elevate on mutating verbs; never widen the scope name or the path allowlist. See `lld.md §15.10`.
9. **Risk colour rule:** `>80 red · 60–80 amber · <60 green` — same everywhere (burn bars, load bars, slip probability).
10. **Babel HTML prototypes** (if ever used): `cdn.jsdelivr.net` not `unpkg`; `type="text/babel"` not `data-type="module"`; never destructure `{style:{}}`.
11. **No prod bug is ever silently discarded.** The shared prod-bug router (`ProdBugRoutingService`) never drops a Jira issue whose `client_code` custom field is missing or unknown — the issue lands in `prod_bug_quarantine` with `client_id = NULL`. Consumers filtering on `(:clientId IS NULL OR j.client.id=:clientId)` skip quarantined rows automatically; global rollups still count them. Never add code that deletes a quarantine row instead of resolving it. See [`hld.md §9 Shared Prod-Bug Routing`](design/hld.md), [`lld.md §5.19 / §6.12`](design/lld.md).
12. **Pages compose, they don't hand-roll.** Page headers, stat rows, filter rows, and table chrome come from the composites (`PageHeader`, `StatGrid`, `FilterBar`, `TableWrap`); status/severity/RAG chips come from `StatusPill` — page-local status→colour maps are banned (extend `StatusPill`'s `TONE` table instead). Branch on `useBreakpoint()`/`useIsMobile()`, never `window.innerWidth`. Reference implementation: `features/alerts/AlertsPage.tsx`; checklist in [`design/frontend-component-strategy.md`](design/frontend-component-strategy.md).
13. **Plans live in `docs/plan/<kebab-name>-plan.md`.** Every implementation plan is written there before coding starts. After each implementation wave ships, update [`design/lld.md`](design/lld.md) (schema, API, formulas) and [`design/hld.md`](design/hld.md) (new subsystems, flows) to reflect what actually shipped — before the wave's commit.
14. **Feature flags are orthogonal to RBAC.** Flags (Features Control Center, `feature_flags` V81) control *release* (`ALL`/`PILOT`/`NONE` + pilot emails); roles control *permission*. Never encode rollout state in `role_screen_config`, never widen role access via a flag. Unknown flag key = visible — create a flag only to hold a feature back; delete it to release. Gate screens with `screen.<navId>` (TopBar + Shell handle it), sections with `<Feature flag="section.<page>.<name>">`. See [`hld.md §12C`](design/hld.md), [`lld.md §5.20`](design/lld.md).
15. **Traceable delivery.** Every substantive change (enhancement, bug fix, task) gets a GitHub issue and/or changelog entry describing the problem and approach *before* implementation starts; on completion, close it with evidence (commit sha, files changed, verification output). Reference the issue in commit messages.

## RBAC quick reference

Roles (V62 persona model): `ADMIN` · `PM` (with per-user `can_edit_budget`) · `ENGINEERING` · `CSM` · `AM` · `REVENUE` · `LEADERSHIP`.

| Role | Radar | CR/Bug/UAT | Man-Days | Capacity | Alerts | Reports | Clients | Agents tab | Admin screens |
|---|---|---|---|---|---|---|---|---|---|
| Admin | ✓ | ✓ | edit | ✓ | ✓ | ✓ | ✓ | full | ✓ |
| PM | ✓ | ✓ | view (edit if `can_edit_budget`) | ✓ | ✓ | ✓ | ✓ | view | ✗ |
| Engineering | ✓ | ✗ | view | ✓ | ✗ | ✗ | ✗ | view | ✗ |
| CSM | ✓ | ✗ | ✗ | ✗ | ✓ | ✓ | ✓ | ✗ | ✗ |
| AM | ✓ | CR+Bug | ✗ | ✗ | ✓ | ✓ | ✓ | ✗ | ✗ |
| Revenue | ✓ | ✗ | view | ✓ | ✗ | ✓ | ✗ | ✗ | ✗ |
| Leadership | read-only | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |

Screen access is data-driven (`role_screen_config` × `screen.<navId>` flags); endpoint gates are `@PreAuthorize` on controllers. Slack-sourced agent invocations are gated to `ADMIN` and `PM` (`AgentInvocationService.SLACK_INVOKE_ROLES`). Full matrix in [`hld.md`](design/hld.md).

## Agents framework — quick map

- **6 native agents** (hardcoded `@Service` beans, keyed by string ID in `NativeAgentRegistry`):
  `report.draft` · `forecast.manday` · `briefing.delivery` · `escalation.draft` · `reminder.overdue` · `standup`
- **Snapshot Reporting Agent** (out-of-band — not in `NativeAgentRegistry`; lives under `service/snapshot/`). `/orbit snapshot` opens a 3-select modal (portfolio · lens · project) → `SnapshotService.request` coalesces (cache · dedup · fresh) → `RadarSnapshotAgent.renderAsync` calls the Playwright sidecar against the live Radar page → DM with `/snapshots/{id}` link that doubles as the progress tracker. Idempotency via partial unique index on `agent_snapshots(dedup_key) WHERE state IN ('PENDING','RUNNING')`. See [`hld.md §9 Snapshot Reporting Agent`](design/hld.md), [`lld.md §5.18 / §15.8 / §15.9`](design/lld.md).
- **Definition-based agents** — user-creatable via Admin → Agents; executed by `AgentRuntime`.
- **Single entrypoint:** `AgentInvocationService.invoke(user, agentKey, args, source)` — routes to native or definition path, persists `AgentRun` with `invocation_source` (e.g. `MANUAL_TEST`, `SLACK_SLASH`, `SCHEDULED`).
- **HITL:** `AgentRuntime` queues `AWAITING_HITL` tool calls and publishes `HitlAwaitingEvent`. Both React inbox and Slack approval card settle via `HitlApprovalService`.
- **Live logs** (planned): `AgentRunStepEvent` → `AgentRunStreamBridge` → STOMP `/topic/agent-runs/{runId}`.

## Hard don'ts

- Don't run `git reset --hard`, `git push --force`, `--no-verify`, or `git config` changes without explicit ask.
- Don't commit `.env` or anything in `target/`.
- Don't bypass `@PreAuthorize` on the controller and rely on UI hiding alone.
- Don't add backwards-compatibility shims for renamed columns / fields — change the call sites.
- Don't add comments that explain WHAT the code does — names already do that. Only comment when WHY is non-obvious.
- Don't relax `escalation.require-hitl`. Don't allow `send_notification` outside the EscalationAgent approval gate.
- Don't expose Slack bot token / signing secret via env vars or YAML — DB is the single source of truth.

## Common bugs & fixes

| Symptom | Likely cause | Fix |
|---|---|---|
| Table missing page controls | Endpoint returns `List<T>` not `Page<T>` | Return `Page<T>`, add `<Pagination>` |
| Alert "Detected by" blank | Caller forgot `alerts.source_agent` on INSERT | Always set `source_agent` |
| 400 on `POST .../reject` | Missing reason in body | Body needs `{reason: "..."}` (→ `outcome_note`) |
| Fresh-install admin login fails | No `ORBIT_ADMIN_PASSWORD` set | `DataInitializer` generates a random password and logs it once at boot — check the backend log, or set `ORBIT_ADMIN_PASSWORD` before first boot |
| Backend changes not picked up | Running JAR is stale | `./scripts/start-all.sh --rebuild` |
| Frontend `style:{}` destructure blank page | Babel-standalone bug | `function C({style}) { ... ...(style||{}) }` |

## When you load this file

1. Read it once.
2. Check [`hld.md`](design/hld.md) before answering architecture questions.
3. Check [`lld.md`](design/lld.md) before writing schema, API, or service code.
4. Look up exact field names in code — don't invent.

If a request conflicts with a locked rule above, **flag it back instead of complying silently.**
