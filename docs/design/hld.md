# Orbit — High-Level Design (HLD)

**Version:** 10.5 | **Date:** July 2026 | **Stack:** React 18 · Java 25 · Spring Boot 3.2 · PostgreSQL 16  
**Change from v10.4 (2026-07-20 — copilot grounding + AI-gateway hardening):** The copilot no longer returns canned dummy text. `AiGatewayService` now (a) treats placeholder/blank API keys as *unconfigured* (the shipped `.env` carries `your_anthropic_api_key`, which previously slipped past the blank-check and forced a 401 → fabricated fallback), (b) honors `AI_PROVIDER` with a real OpenAI path alongside Anthropic, and (c) replaces the data-fabricating fallback with honest, data-free notices ("AI isn't configured" / "couldn't reach the model") — it never invents issue keys or names. New `CopilotContextService.buildDigest(portfolioId)` composes a cheap, portfolio-scoped snapshot (top-5 open alerts + severity counts, open-CR stage rollup, prod-bug severity, capacity load) that `CopilotController.buildGroundedPrompt` injects into the system prompt, so answers are grounded in live delivery data; the frontend passes the active `portfolioId`. Heavy per-project risk scoring is intentionally out of the synchronous digest. See §4 and lld §5.10.  
**Change from v10.3 (2026-07-20 — topbar shell + Orbit launcher):** The app shell drops the left `Sidebar` (and its mobile hamburger/drawer) for a topbar-only chrome per the V3 mockup. `TopBar` carries the `◈ orbit` brand (→ `/orbit`), an alerts bell (gated → `/alerts`), and an avatar dropdown (gated admin/system links + theme + Sign out). Product-screen navigation moves to a new `/orbit` launcher (`OrbitLauncherPage`: hero Orbitter tile + gated `PRODUCT_NAV` tiles); login still lands on `/radar`. The nav model + RBAC×flag gate are consolidated into `layout/nav.tsx` (`PRODUCT_NAV`, `ADMIN_NAV`, `useAllowedScreenIds`, `visibleNav`) and shared by both surfaces, so rule 14 (screen = role × `screen.<navId>` flag) is preserved and direct-URL gating in `Shell` is unchanged. See §7 "Shell UX" and lld §2.  
**Change from v10.2 (2026-07-17 — metric-correctness floor Wave 1):** The AM dashboard's shared metric definitions are being consolidated so widgets that report the same concept can never silently disagree. Wave 1 tackled SLA: `SlaBucketService` (`service/am/`) is now the single canonical 3-bucket classifier (met / near = last 25% of the window / breached; adherence = met/tracked), and the stage matrix, client overview, POD score and DH-metrics pillar all route through it instead of each carrying its own formula (previously 2-bucket in three of the four). A **reconciliation harness** (`AmDashboardControllerTest.slaBucketsReconcileAcrossMatrixOverviewAndPodScore`) feeds one CR set through all three repository shapes and fails if any surface's met/near/breached diverges — the guardrail against future drift. This is the correctness floor that precedes letting an agent *act* on these numbers (Phase 2, the SLA-breach escalation loop). **Phase 2 Wave 2** adds the detector: `SlaBreachSweep`, a scheduled (default-off) service that finds open CRs breaching (or near-margin on) their canonical stage SLA, dedups via the `cr_escalation` ledger (V90), and correlates owner availability — returning escalation-worthy candidates. **Wave 3** wires the send: `SlaBreachSweep` (when enabled) drafts a Slack nudge per candidate via `AiGateway` (template fallback) and runs a find-or-seeded system `AgentDefinition` ("SLA Breach Escalation", tool = `slack.send_channel`) through `AgentRuntime`, so the tool is queued `AWAITING_HITL` and a human approves via the Slack card before anything sends (`HitlApprovalService` → `AgentDecisionLog`; rule 3 upheld). The `cr_escalation` ledger dedups. This makes it Orbit's first *standing* agent that turns a metric into a proposed action a human approves — a dashboard detects and proposes nothing; this does both. **Enabled**: `orbit.agents.sla-escalation.enabled=true`; HITL keeps it safe (it only posts approval cards, never auto-sends, and needs a Slack channel mapping to reach anyone). The whole chain is verified through the real Spring context by `SlaEscalationLoopIntegrationTest` — seed a breaching CR → sweep proposes → `slack.send_channel` queued `AWAITING_HITL` and *not* sent → `HitlApprovalService.approve` fires the send + writes the `AgentDecisionLog` (SlackService/AiGateway mocked, so no real workspace/AI call).

**Change from v10.1 (2026-07-15 — AM widget-parity Wave 1):** Account Detail (`/accounts/:projectId`) is now the **client master page** with six tabs — Overview · Delivery Speed · Delivery Quality · Delivery Predictability · Teams · Account Workbench. Delivery-health pillar tabs render live Speed/Quality metrics (lead time, throughput, stage-SLA compliance, backlog aging, incidents) from `/am/client/{id}/dh-metrics`, with a health ring weighted by admin-configurable pillar weights (`am_settings` V84, `GET/PUT /api/v1/am/settings`) and vs-POD-average comparison; metrics without a data feed stay behind `section.client.dh.*` flags as "feed pending" cards. Overview gained the mock's KPI row (CSAT · Open CRs · Utilization · Prod Bugs), SLA & BAU card (adherence + Breached·Near·Met), per-client stage-SLA drill table, summary tiles, and the phase-grouped Sprint Scope tracker (`GET /accounts/{id}/sprint-scope`, flag `section.client.sprint-scope` = ALL); the mandays strip and 3-column worklists were retired into these (one-fact-one-place). AM home: POD cards deep-link to the W11 work-mix drill (`GET /am/csat-drill`), scorecard tiles navigate to the client master page, "On Hold" normalises into the Hold bucket, and V84 fixed the stage-SLA audit gap by seeding targets for the live Jira stage vocabulary. A reconciliation test pins summary == matrix == owner-share totals.

**Wave 2 addendum (2026-07-15, same plan):** F1 admin-entered CSAT + engagement per client (`clients.csat_launch/csat_bau/engagement_score` V85, Admin → Clients) unlock the mock POD score — min-max normalized 60% CSAT + 40% SLA adherence with SLA-only fallback — and the client-page CSAT KPI/engagement rows. F2 Jira custom-field mappings (`jira_config` story-points/Sprint/SM/PjM ids, edited in Jira Sync → Field mapping) feed `jira_issues.story_points/sm_owner/pjm_owner/current_sprint_*` through the shared `JiraFieldMapper` (JQL sync + webhook), lighting up the Solutioning-Manager and PjM owner donuts (`/am/owner-share?dim=`, setup hint when unmapped). W3 client health chips = weighted DH pillar score (D3) with sort-by-health. F4 adoption deep link stored in `am_settings.adoption_url` with an admin inline editor on the AM home Adoption card.

**Wave 3 addendum (2026-07-15, same plan — Jira sprint & changelog subsystem):** New F3 pipeline under `service/sync/`: the webhook's previously-ignored `changelog` block and an admin-triggered resumable backfill (`POST /jira-sync/backfill-changelog`) both feed a generic `issue_transitions` field-change ledger (dedup on changelog id; derived values recomputed, never incremented). Sprints are modelled from the issue Sprint custom-field payload — no Board API — with membership add/remove times from changelog diffs, a scheduled metadata refresh (`/rest/agile/1.0/sprint/{id}`), and a committed-SP snapshot at sprint activation (D4; pre-rollout sprints are flagged approximate). `VelocityService` derives committed-vs-delivered velocity (`/am/velocity`, W5 section + POD/scorecard velocity rows), sprint-derived milestones (W16), cycle time, and the Predictability pillar (commitment / spillover / scope-change) for the client master page. All sprint-fed UI self-manages an "awaiting sprint data" state until the Sprint + story-points fields are mapped and a sync + backfill has run.

**Change from v10.0** *(historical)*: Account Detail deep-dive page (`/accounts/:projectId`) with aggregator endpoint `GET /api/v1/accounts/{projectId}` returning weekly status · mandays · launch/BAU ops · risk register · production issues tracker · release calendar · 3-column worklists · internal + client team contacts · health & sentiment block · ops-model selector. New tables `project_team`, `project_risks`, `project_releases` (V69). Click-through from Orbitter account cards. PDF export via `window.print()`. Workbench tab with team and health sub-views.

**Change from v9.0 → v10.0** *(historical)*: Multi-client portfolios (M:N) · Project life-stage health scoring · SLA rules engine (default + override + hourly recompute) · Darwinbox full integration · Google SSO · Jira issue-type rule: project bugs default to UAT · `BugController` filter wiring with null-safe counts · backend package rename `com.gauge` → `com.orbit` · V64–V68 migrations.

**Change from v8.0 → v9.0** *(historical)*: Role rename (PJM→PM, HEAD_PJM→ADMIN, ENG_MANAGER→ENGINEERING) · `canEditBudget` JWT claim + `AppUser` column · `PUT /man-days/budget` checks claim for non-Admin PM · `GET /man-days/portfolio-summary` · `portfolioId` filter on CR list + stage-summary · `GET /portfolios/{id}/summary` · V62 migration · `GlobalCopilotPanel` (Shell-level floating) · Login tagline "Delivery Command Center" · Sidebar: Orbitter home (id: `radar`), My Today/cockpit removed, no Jira Live section.

---

## 1. Product Overview

Orbit is an AI-native delivery intelligence platform for two primary personas:

| Persona | Primary need | Entry point |
|---|---|---|
| **PM** | "What needs my attention right now?" | Orbitter (Portfolio Radar, home) — AI briefing + predicted slip probabilities + AI copilot |
| **ADMIN / Leadership** | "Which projects will miss commitments?" | Orbitter (Portfolio Radar, home) — AI briefing + predicted slip probabilities |

Built on the BandViz bandwidth management foundation (Java 25 / Spring Boot / PostgreSQL), extended with Jira lifecycle tracking, agentic AI workflows, and ML models.

---

## 2. System Architecture

```
┌───────────────────────────────────────────────────────────────────────────┐
│                      FRONTEND (React 18 + TypeScript)                      │
│                                                                             │
│  Orbitter (Portfolio Radar, home) │ CR Board │ Bug Triage │ UAT Tracker     │
│  Man-Days │ Alert Center │ Reports │ Capacity │ Client Backlog             │
│  Agent Audit Log                                                             │
│  Admin: SLA Rules │ Lifecycle Mapping │ User Management │ Report Schedules  │
│  Admin: Agent Builder │ Notification Rules │ Phase Deliveries               │
│  Integrations (Jira · HR System · Slack)                                    │
│                                                                             │
│  GlobalCopilotPanel — floating button (Shell-level, all pages/roles)        │
│  Collapsible sidebar with 14-day project heat strips                        │
└──────────────────────────────┬────────────────────────────────────────────┘
                                │ HTTPS/REST + WebSocket
┌──────────────────────────────▼────────────────────────────────────────────┐
│             Spring Boot 3.2 — API Gateway Layer                             │
│             JWT Auth (RS256) · RBAC (@PreAuthorize) · Rate Limiting         │
│             Request Logging · OpenAPI/Swagger at /swagger-ui.html           │
└──────┬──────────────┬──────────────┬────────────────┬──────────────────────┘
       │              │              │                │
┌──────▼───┐  ┌───────▼───┐  ┌──────▼───┐  ┌────────▼─────────────────────┐
│ Delivery  │  │ Capacity   │  │ Report   │  │  Agent Framework              │
│ Services  │  │ Services   │  │ Engine   │  │  AgentRuntime · ToolRegistry  │
│CR·Bug·UAT │  │ManDays     │  │          │  │  HITL Gateway · Memory Store  │
└──────┬────┘  └───────┬───┘  └──────┬───┘  └────────┬─────────────────────┘
       │               │             │                │
┌──────▼───────────────▼─────────────▼────────────────▼─────────────────────┐
│                        Data & Integration Layer                              │
│  PostgreSQL 16 + pgvector  │  Redis 7  │  Jira Cloud REST v3 + Webhooks    │
│  Prophet Forecast Sidecar (FastAPI)  │  OpenAI/Claude LLM                  │
│  Slack API (Bot Token)  │  SendGrid SMTP  │  MS Teams Connector             │
└────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Agent Framework Layer (v4.0 addition)

The Agent Framework replaces ad-hoc per-agent boilerplate with a centralised execution engine. All agents — whether built-in or user-defined via the Agent Builder — run through the same runtime, share the same tool catalogue, and pass through the same HITL enforcement point.

```
┌────────────────────────────────────────────────────────────┐
│                    TRIGGER LAYER                            │
│  Cron │ Jira Webhook │ Threshold Watcher │ Manual │ UI     │
└──────────────────────┬─────────────────────────────────────┘
                       │
                       ▼
┌────────────────────────────────────────────────────────────┐
│                  AGENT RUNTIME                              │
│  AgentDefinition → ContextBuilder → Tool Loop → Audit      │
│                         ↕                                  │
│                   HITL Gateway                             │
└───────┬──────────────────────────────────────┬────────────┘
        │                                      │
        ▼                                      ▼
┌──────────────────┐                ┌─────────────────────┐
│  TOOL REGISTRY   │                │   MEMORY STORE      │
│ orbit.*          │                │ Short: Redis 7d TTL │
│ slack.*          │                │ Long:  agent_memory │
│ email.*          │                │        (pgvector)   │
│ jira.*           │                └─────────────────────┘
│ memory.*         │
└──────────────────┘
```

**AgentRuntime** receives an `AgentDefinition` (either a built-in class or a persisted DB row created via Agent Builder), builds context (loads memory, resolves available tools), runs the ReAct tool loop, enforces the HITL Gateway for any external-channel tool call, writes the outcome to `agent_runs` and `agent_tool_calls`, and updates memory.

**ToolRegistry** is the single catalogue of callable integrations. Every tool implements `AgentTool` and is registered at startup. Agents declare which tool IDs they are permitted to call; the runtime enforces this at call time.

**HITL Gateway** is the sole enforcement point for all external-channel writes. Any tool in `slack.*`, `email.*`, or `jira.*` that is flagged `hitlRequired=true` will pause the run, surface a proposal to the copilot panel, and wait for PJM approval before execution. This replaces the previous per-agent HITL logic.

**Memory Store** is two-layered:
- **Short-term** (Redis, 7-day TTL): last run output per agent+project. Key pattern: `agent:{id}:project:{pid}:{key}`. Read at run start via `memory.read`; written at run end via `memory.write`.
- **Long-term** (PostgreSQL `agent_memory` table, permanent): persisted facts, decisions, and cross-run patterns. Queryable via `memory.search` (pgvector semantic search).

---

## 3. Module Map (v4.0 — aligned with mock screens)

| Module | Epic | Screen(s) | Core responsibility |
|---|---|---|---|
| Jira Sync Service | EPIC-01 | Jira Sync Health (4 tabs) | Webhook ingestion · delta pull · JQL config · field mapping · webhook config |
| Report Engine | EPIC-02 | Reports + Report Schedules (Admin) | LLM-drafted reports · Word/PDF export · auto-scheduled delivery |
| CR Delivery Module | EPIC-03 | CR Board | CR lifecycle · stage lanes · milestone tracking · detail drawer |
| Bug Triage Module | EPIC-04 | Bug Triage — Prod tab | Prod bug SLA · severity · reopen tracking · BERT auto-tag |
| UAT Module | EPIC-05 | UAT Tracker (standalone) | UAT cycles · retest rounds · sign-off blockers · client sign-off gate |
| Client Backlog Module | EPIC-06 | Client Backlog | Health cards · dependency tracker · governance · milestone calendar |
| Alert Engine | EPIC-07 | Alert Center | Cross-module risk detection · acknowledge · assign with follow-up date · phase column + days overdue |
| Notifications Engine | EPIC-10 | Notification Rules (Admin) · Phase Deliveries (Admin) | Delivery phase tracking · T-2/T-1/D-Day Slack reminders · overdue auto-flag · escalation chains · notification event log |
| Capacity Engine | EPIC-08 | Capacity & Team · Man-Days | MD consumption + budget edit · leave calendar · assignments |
| Admin Service | EPIC-09 | SLA Rules · Lifecycle Mapping · User Mgmt · Report Schedules · Agent Builder · Integrations | RBAC · SLA config · Jira mapping · per-client health thresholds · agent config · Slack setup |
| Agent Framework | NEW | GlobalCopilotPanel (floating, Shell-level) · Agent Audit Log · Agent Logs · Agent Builder · Integrations | AgentRuntime · ToolRegistry · HITL Gateway · HitlApprovalService · Memory Store · 6 AI agents · Slack integration |
| Prediction Module | NEW | Orbitter (Portfolio Radar) · Man-Days forecast | Slip probability · Prophet CI-band forecasting · BERT triage · embeddings |
| Release Control | NEW 2026-07-08 | Features Control Center (Admin) | Feature-flag controlled rollout — audience `ALL`/`PILOT`/`NONE` + pilot emails · gates screens (`screen.<navId>`: sidebar + route) and page sections (`section.<page>.<name>`) · independent of RBAC · ADMIN bypass · see §12C |

---

## 4. Agent Framework

### 4.1 Core Infrastructure

#### AgentTool Interface
Every callable integration in the Tool Registry implements:
```java
public interface AgentTool {
    String toolId();                    // e.g. "slack.send_channel"
    String description();               // shown in Agent Builder tool picker
    boolean isHitlRequired();           // true → HITL Gateway pauses run for approval
    JsonNode execute(JsonNode args, AgentRunContext ctx) throws AgentToolException;
}
```

#### ToolRegistry
Singleton Spring bean. Discovers all `AgentTool` beans on startup and indexes by `toolId()`. Enforces that only tools declared in `AgentDefinition.allowedTools[]` can be called during a run.

| Tool ID | HITL | Description |
|---|---|---|
| `orbit.get_cr_summary` | No | Get CR counts and stage breakdown for a project/client |
| `orbit.get_overdue_items` | No | CRs/bugs past SLA or hold threshold, with age |
| `orbit.create_alert` | No | Create an Orbit alert with source_agent attribution |
| `orbit.get_issue_detail` | No | Get full detail for a Jira issue by key |
| `orbit.get_team_capacity` | No | Current utilisation and leave for assignees |
| `orbit.get_milestone_status` | No | Milestone dates and status for a project/CR |
| `orbit.get_man_day_status` | No | Current burn, remaining, and exhaustion forecast |
| `orbit.get_cr_summary` | No | CR counts per lifecycle stage |
| `orbit.get_bug_summary` | No | Bug count, SLA status, P0/P1 breakdown |
| `orbit.get_release_calendar` | No | Upcoming milestone dates across projects |
| `orbit.semantic_search_past_issues` | No | Semantic search over issue_embeddings |
| `orbit.get_issue_activity_since_yesterday` | No | Yesterday's Jira transitions and comments |
| `orbit.get_leave_today` | No | Who is on leave today per project |
| `orbit.get_sprint_remaining_days` | No | Days remaining in current sprint |
| `orbit.get_man_day_history` | No | Historical daily burn snapshots |
| `orbit.run_forecast_model` | No | Call Prophet sidecar to generate forecast |
| `orbit.get_upcoming_leaves` | No | Approved leave in next 30 days |
| `orbit.get_stakeholder_contacts` | No | Client and internal stakeholder contact list |
| `orbit.render_report_docx` | No | Render sections to Word/PDF via ExportService |
| `slack.send_channel` | No | Post a message to the project's configured Slack channel (channel resolved from project mapping → default config) |
| `slack.send_dm` | **Yes** | Send a direct message to a user by email via Slack |
| `email.send` | **Yes** | Send an email to one or more recipients |
| `jira.comment` | **Yes** | Add a comment to a Jira issue |
| `jira.transition` | **Yes** | Change a Jira issue's status |
| `memory.read` | No | Read the agent's last known state for this project (Redis) |
| `memory.write` | No | Persist a key-value fact for the next run (Redis + DB) |
| `memory.search` | No | Semantic search across the agent's long-term memory (pgvector) |

#### AgentRuntime
Execution loop:
1. Load `AgentDefinition` from DB (or built-in class for the 5 original agents)
2. Build context: call `memory.read` for short-term state; run `memory.search` for relevant past facts; load live event data
3. Run ReAct loop: LLM reasons → emits tool calls → runtime dispatches each via ToolRegistry
4. On any HITL-required tool call: pause loop → emit `{type:"proposal"}` WS event → wait for PJM approval / rejection / edit
5. After loop completion: write run summary to `agent_runs`; write each tool call to `agent_tool_calls`; call `memory.write` to persist relevant output
6. Emit `{type:"done"}` WS event

#### HITL Gateway
Single enforcement point. Called by AgentRuntime before every tool execution. If `tool.isHitlRequired()`:
- Persist a proposal record to `agent_proposals`
- Send WS proposal event to PJM
- Block the virtual thread until outcome is recorded
- On APPROVED: execute tool, record outcome
- On REJECTED: skip tool, store `outcome_note` (mandatory), continue or abort run
- On EDITED: replace args with edited payload, execute, record outcome

This replaces the previous per-agent HITL logic. `escalation.require-hitl: true` remains in config and is enforced at the Gateway level.

### 4.2 The Six AI Agents

#### DeliveryIntelligenceAgent
- **Type:** INTELLIGENCE
- **Trigger:** Every Jira webhook event · daily 8am · sprint boundary event
- **Loop:** Load project memory → LLM reasons (ReAct) → parallel tool calls → synthesise → propose action → HITL gate → act → write to memory
- **Tools:** `orbit.get_issue_detail`, `orbit.get_team_capacity`, `orbit.get_milestone_status`, `orbit.semantic_search_past_issues`, `jira.comment`
- **Output:** Risk summary on radar/cockpit · alert with NL explanation · Jira comment (HITL-gated) · sprint boundary summary
- **Mock surface:** Cockpit copilot messages · alert `source_agent` label · agent audit log
- **Colour:** `C.indigo`

#### ReportDraftingAgent
- **Type:** REPORTING
- **Trigger:** Manual PJM request · scheduled (configured via Report Schedules admin) · sprint close
- **Loop:** Query live data → LLM writes narrative sections → PJM edits inline → publish/export
- **Tools:** `orbit.get_cr_summary`, `orbit.get_bug_summary`, `orbit.get_man_day_status`, `orbit.get_release_calendar`, `orbit.render_report_docx`
- **Output:** Editable draft inline in Reports screen · downloadable Word/PDF · scheduled email delivery
- **Mock surface:** Reports screen inline preview · copilot tool-call trace (4 tools shown)
- **Colour:** `C.purple`

#### ManDayForecastAgent
- **Type:** FORECAST
- **Trigger:** Daily · when allocation changes >5%
- **Loop:** Feed burn history to Prophet sidecar → receive forecast + CI bands → LLM interprets in project context → propose actions
- **Tools:** `orbit.get_man_day_history`, `orbit.run_forecast_model`, `orbit.get_upcoming_leaves`
- **Output:** Forecast chart with 80%/95% CI bands · NL interpretation · `proposedActions[]` list · alert if exhaustion <30 days
- **Mock surface:** Man-Days burn chart · agent warning banner · forecast proposed actions section
- **Colour:** `C.teal`

#### StandupAgent
- **Type:** STANDUP
- **Trigger:** Daily 8am per project (Mon–Fri)
- **Loop:** Query yesterday's Jira activity + today's leave → LLM generates 3-bullet standup → PJM approves or auto-posts after 30-minute countdown
- **Tools:** `orbit.get_issue_activity_since_yesterday`, `orbit.get_leave_today`, `orbit.get_sprint_remaining_days`
- **Output:** Standup draft card in cockpit with "Auto-posts in Xm" countdown · one-click post to Slack/Teams
- **Mock surface:** Cockpit standup card · countdown chip
- **Colour:** `C.green`

#### EscalationAgent
- **Type:** ESCALATION
- **Trigger:** Alert crosses severity threshold · hold aging > configured days (default 5)
- **Loop:** Decide escalation target (delivery lead / exec / client) → LLM drafts message in tone appropriate for recipient → HITL approval always required
- **Tools:** `orbit.get_stakeholder_contacts`, `slack.send_channel`, `slack.send_dm`, `email.send`
- **Output:** Draft message in copilot panel for PJM approval before any send
- **Mock surface:** Alert action panel "Draft escalation ↗" · audit log entry with HITL outcome
- **Colour:** `C.red`

#### DevReminderAgent *(new in v4.0)*
- **Type:** REMINDER
- **Trigger:** Configurable threshold (default: daily, or when overdue items exceed N)
- **Loop:** Query overdue items for a project → filter by assignee → LLM drafts a concise reminder message → HITL approval → post to project Slack channel
- **Tools:** `orbit.get_overdue_items`, `slack.send_channel`, `memory.read`, `memory.write`
- **HITL:** Always required before `slack.send_channel` executes
- **Output:** Slack message to project channel listing overdue CRs/bugs with assignees
- **Colour:** `C.amber`

#### ClientUpdateAgent *(placeholder — Sprint 8)*
- Planned agent for client-facing update summaries. Colour: `C.blue`. Full spec in Sprint 8.

### 4.3 Agent Colour Map (complete)

```javascript
const agentColorMap = {
  "DeliveryIntelligenceAgent": C.indigo,
  "ManDayForecastAgent":       C.teal,
  "StandupAgent":              C.green,
  "EscalationAgent":           C.red,
  "ReportDraftingAgent":       C.purple,
  "DevReminderAgent":          C.amber,
  "ClientUpdateAgent":         C.blue,
};
// Pattern: color + "18" for background, color for text
// e.g. background: agentColorMap[agent] + "18", color: agentColorMap[agent]
```

### 4.4 Agent Builder (Admin screen)

Allows Admins to define, configure, and test new agents from the UI without writing Java code. Stored in the `agent_definitions` table. Built-in agents (the original 5 + DevReminderAgent) cannot be deleted but can be disabled.

Key capabilities:
- Define agent name, type (INTELLIGENCE / REMINDER / REPORTING / ESCALATION / FORECAST / STANDUP / CUSTOM), and trigger (CRON / WEBHOOK / THRESHOLD / MANUAL)
- Select allowed tools from the ToolRegistry catalogue (tool picker shows HITL indicator)
- Write or edit the system prompt in a code editor panel
- Set Slack channel (from project-channel mappings) for output
- Toggle HITL on/off per agent (some tools override this — HITL-required tools are always gated)
- Enable/disable agent without deleting it

#### Agents admin surface (v10.2 — being overhauled)

The Agents screen is now structured as a **list + detail** UX:

| Surface | Route | Purpose |
|---|---|---|
| List | `/admin/agents` | One row per agent definition. Columns: name, type, trigger, tools, HITL, enabled, last-run status. Top-level "Pending approvals" cross-agent inbox stays here. |
| Detail | `/admin/agents/:id` | Sub-tabs: **Overview** (read-only definition + edit button) · **Runs** (paginated history scoped to this agent) · **Live** (STOMP-streamed tail of the currently-executing run) · **Test** (form to construct args; on submit → Live tab with the new runId). |

- **Live tab** subscribes to `/topic/agent-runs/{runId}` while mounted; unsubscribes on tab change / page leave.
- **Test run** is `ADMIN`-only and goes through `AgentInvocationService.invoke(user, agentKey, args, source="MANUAL_TEST")`.
- **HITL** events emitted during a run surface inline in the Live tab with an "Approve here" button that opens the same approval modal as the cross-agent inbox.
- Run history continues to be queried via `GET /admin/agents/{id}/runs` (paginated) and steps via `GET /admin/agents/{id}/runs/{runId}/steps`.

See [`agents-tab-overhaul-plan.md`](agents-tab-overhaul-plan.md) for the in-flight implementation plan.

### 4.5 HITL Rules (v5.0 update)

- Every tool with `requiresHitl()=true` pauses the run and surfaces a proposal
- REJECTED proposals require `outcome_note` text — mandatory, stored in `agent_decision_log`
- `email.send`, `jira.comment`, and `jira.transition` are HITL-required
- `slack.send_channel` is **NOT** HITL-required (v5.0 change) — Slack sends fire immediately; agents only need approval for emails and Jira writes
- `escalation.require-hitl: true` config applies globally for the EscalationAgent — never relaxed
- Token count stored in `agent_runs.tokensUsed` for every run; per-call breakdown in `agent_tool_calls`

---

## 5. Model Layer

| Model | Technology | Purpose | Surfaces in mock |
|---|---|---|---|
| LLM Core | GPT-4o or Claude Sonnet (env-configurable) | Agent reasoning · report narrative · escalation drafts · AI briefing | Copilot messages · radar briefing bullets |
| Embedding model | text-embedding-3-small | Semantic search · CR/bug similarity · duplicate detection · agent memory search | Copilot tool-call trace: `semantic_search(...)` |
| Forecast model | Facebook Prophet via FastAPI sidecar | Man-day burn forecasting with 80%/95% CI bands | Man-Days chart with CI band shading |
| Triage classifier | GPT-4o few-shot (v1) → fine-tuned BERT (v2) | Auto-tag issue type · predict severity · suggest owner | Bug Triage: "Suggested by AI: P1 · Assign Kavya T." chip |

---

## 6. Role-Based Access Control

Decisions from alignment review applied: role names renamed PM/ENGINEERING; budget edit now PM with `canEditBudget=true` JWT claim or ADMIN; Agent Builder and Integrations screens are ADMIN-only.

| Role | Screen access | Edit rights | Notes |
|---|---|---|---|
| **ADMIN** | All screens | Full — settings, lifecycle mapping, SLA rules, user mgmt, report schedules, agents screen, integrations, budget edit | Sreekanth M. in mock; merged from former "Head of PJM" and "Admin" roles |
| **PM** | Orbitter (radar) · CR Board · Bug Triage · UAT Tracker · Man-Days (view; edit if `canEditBudget=true`) · Reports (generate+view) · Alert Center · Client Backlog · Copilot | Can acknowledge/assign alerts · generate reports · approve agent proposals · edit budget only if JWT `canEditBudget` claim is `true` | Priya K. in mock |
| **LEADERSHIP** | Portfolio Radar only — read-only, natural-language Q&A via copilot | None | Rajesh N. in mock |
| **ENGINEERING** | Orbitter (radar) · Capacity & Team · Man-Days (view) · leave calendar | Can add/edit assignments and leaves | Kavitha R. in mock |
| **CSM** | Orbitter (radar) · Client Backlog · Alert Center · Reports (read) | Read-only on client backlog, alerts, reports | Client success managers |
| **REVENUE** | Orbitter (radar) · Man-Days (view) · Reports (read) · Capacity (view) | Read-only | Revenue/commercial team |

Budget edit: ADMIN always; PM only when `canEditBudget=true` is present in JWT claims (set via `AppUser.canEditBudget` DB column). No client viewer role. Internal tool only.

**Feature flags are orthogonal to RBAC** *(2026-07-08)*: roles answer "may this role use this screen", flags answer "is this feature released yet, and to whom". Both gates apply independently; a screen must pass its role check **and** its `screen.<navId>` flag to render. Flags are managed in the Features Control Center (ADMIN-only) and never widen role access — ADMIN bypass shows flagged-off features to admins for pre-release verification only. See §12C.

---

## 7. Frontend Screen Inventory (v4.0)

19 navigable screens across 3 sidebar sections (v5.0 adds Notification Rules, Phase Deliveries, consolidates Jira/Darwinbox/Slack under Integrations; v6.0 adds Agents screen with built-in logs/HITL; v7.0 merges Agent Builder + Agent Logs into one Agents screen — net −1 screen; v9.0 removes My Today/cockpit — net −1 screen; 2026-07-08 adds Features Control Center — net +1 screen).

**WORKSPACE (8 screens)**
1. Orbitter (Portfolio Radar, id: `radar`) — home screen · AI briefing bullets · slip probabilities · project cards with heat strips · expandable insight
2. CR Board — stage lane counters · filterable table · click-to-expand milestone detail drawer
3. Bug Triage — Prod bugs tab (SLA tracking) + UAT bugs tab (entry point to UAT Tracker)
4. Man-Days — burn table + drill-down with forecast chart · CI bands · proposed actions · Edit Budget modal (ADMIN or PM with canEditBudget=true)
5. Alert Center — list with severity filter + action panel (acknowledge · assign+date · escalate · dismiss)
6. Reports — history table + inline preview + generate modal · Schedules tab
7. Capacity & Team — Team load tab · Leave calendar tab · Assignments tab
8. Client Backlog — 5 health cards · drill-down with CR/bug/milestone summary · dependency tracker · contact

**SYSTEM (2 screens)**
9. Agent Audit Log — Decision list · inspector panel · token/cost summary
10. Integrations *(v5.0: unified)* — three tabs: **Jira** (sync runs · field mapping · JQL config · webhook config) · **HR System** *(generic HRMS connector card — provider dropdown + descriptor-driven settings form; sync runs · employee mapping · leave data · WFH data · leave balances · attendance · settings)* · **Slack** (bot token · workspace · channel mapping per project · test send)

**Copilot** — not a navigable screen; `GlobalCopilotPanel` is a floating button rendered at Shell level, accessible to all authenticated users on every page (v9.0: moved from inline `CopilotPanel.tsx` to `GlobalCopilotPanel.tsx`).

**ADMIN (9 screens)**
11. SLA Rules — global + client-specific · add/edit modal
12. Lifecycle Mapping — Jira status → Orbit stage + issue type · auto-discover
13. User Management — RBAC table · invite modal
14. Report Schedules — schedule list · add modal
15. Agents *(v7.0: merged)* — three tabs: **Agents** (agent list · create/edit modal · tool picker · test-run with per-step panel · ▾ Runs inline history) · **Execution logs** (cross-agent run list · per-run step expansion · agent/status filter) · **Pending approvals** (HITL inbox · approve/reject with mandatory reason · auto-refresh 30s) — accessible to ADMIN only
16. Notification Rules *(v5.0)* — four tabs: **Rules** (enable/disable T-2/T-1/D-Day/digest rules per role/phase) · **Escalation Matrix** (Phase SPOC email per role/phase) · **Global SPOCs** (Delivery SPOC · Solutions SPOC · Eng Manager) · **Event Log** (sent/responded notifications)
17. Phase Deliveries *(v5.0)* — per-project phase schedule editor · add/edit FSD/DEV/QA/UAT/PROD phases with dates and assignees · phase summary strip showing status per phase
18. Admin Console — Clients · Projects · Portfolio · Users · Roles · SLA Rules · Lifecycle Mapping · Report Schedules · Notification Rules · Phase Deliveries
19. Features Control Center *(2026-07-08, id: `flags`)* — feature-flag table (key · description · audience · pilot emails) · inline create/edit · pagination · deleting a flag releases the feature to everyone

**AUTH (1 screen)**
20. Login — email/password + Google SSO + Okta SSO · tagline: "Delivery Command Center" · no version number shown

**Shell UX** (v10.4 — supersedes the v9.0 sidebar):
- **Topbar-only chrome** (no left sidebar, no mobile drawer): sticky `TopBar` with `◈ orbit` brand (→ `/orbit`), an alerts bell (gated → `/alerts`), and an avatar dropdown holding the gated admin/system links + theme toggle + Sign out.
- **Orbit launcher** (`/orbit`, `OrbitLauncherPage`): the hub for all product screens — a hero "Orbitter" (Radar) tile + a gated grid of `PRODUCT_NAV` tiles. Reached via the brand.
- Default landing stays `/radar`; `/` and unknown paths redirect there.
- Screen visibility is RBAC (`role_screen_config` / `ROLE_ACCESS` fallback) × feature flag (`screen.<navId>`), computed once in `layout/nav.tsx` and shared by the topbar dropdown and the launcher (rule 14 preserved).

---

## 8. AI Briefing & Prediction Design

### Portfolio Radar briefing
- Generated by DeliveryIntelligenceAgent at 8am daily + on-demand
- Input: all active projects' latest risk scores, alerts, burn data, milestone status
- Output: 3–5 bullet points sorted by severity, each with a confidence indicator
- Confidence: HIGH (≥3 corroborating signals), MEDIUM (2 signals), LOW (1 signal or inferred)
- Cached in Redis (TTL 2h); invalidated on any new CRITICAL alert

### Slip probability score
- v1 (heuristic, synchronous): sigmoid of weighted alert/milestone/burn score
- v2 (LLM-augmented, async): LLM adds narrative explanation, updates `alerts.ai_explanation`
- Displayed: percentage on project card + heat strip (14-day risk history)

### BERT auto-tag
- Fires on every new issue sync (Jira webhook or delta pull)
- Suggests severity (P0–P3) and owner based on issue text + historical patterns
- Shown in UI as dismissible "AI suggestion" chip in Bug Triage and CR Board
- PJM can accept (one click) or override (manual edit)

---

## 9. Integration Architecture

### Jira Cloud
- **Webhook endpoint:** `POST /api/jira/webhook` — idempotent, HMAC-SHA256 validated via `X-Hub-Signature-256`
- **Events:** `issue:created`, `issue:updated`, `issue:deleted`, `comment:created`
- **Delta pull:** Every 10 min (safety net for missed webhooks) — cron `0 */10 * * * *`
- **Per-project JQL:** Configurable per project via Jira Sync Health > JQL Config tab
- **Retry:** 3 retries with exponential backoff; dead-letter queue after 3 failures
- **Field mapping:** Per-client custom field IDs for BRD/FSD dates — configurable via Jira Sync Health > Field Mapping tab
- **Webhook config UI:** Endpoint URL · HMAC secret display · event list · project scope — shown in Jira Sync Health > Webhook Config tab

### Slack API *(v5.0 update)*
- **Connector:** `SlackService` — wraps the Slack Web API using a Bot Token (`xoxb-...`)
- **Configuration:** Stored in `slack_config` table; managed via Integrations > Slack tab
- **Config fields:** workspace name · bot_token · signing_secret · default_channel
- **Per-project channels:** `slack_project_channels` table maps each project to a Slack channel ID. Managed via Integrations > Slack > Project channel mapping.
- **Channel resolution (v5.0):** `resolveChannel(projectId)` checks project mapping first, falls back to `default_channel` from config. Tools receive a resolved channel ID — never "general" or a name.
- **Response validation (v5.0):** `sendToChannelDetailed()` parses the Slack API response body and checks `"ok"` field — Slack always returns HTTP 200, actual errors are in the body. Result map includes `{ok, channel, ts, error}`.
- **HITL:** `slack.send_channel` is **not** HITL-gated (v5.0 change). Slack posts are low-risk and fire immediately. Email and Jira writes remain HITL-gated.
- **Tools:** `slack.send_channel` (project channel) · `slack.send_dm` (by email, resolves Slack user ID via `users.lookupByEmail`)
- **Notifications Engine:** `NotificationSchedulerService` also uses `SlackService` to send T-2/T-1/D-Day delivery reminders and overdue escalations (separate from agent-triggered sends)

#### Bidirectional Slack agent (Phases 1–5)

Slash command `/orbit`, `@orbit` mentions, and DMs to the bot all route through `IntentResolver` (deterministic parser + Haiku LLM fallback) → `SlackToolExecutor` → `SlackResponseRenderer` → `SlackInteractionRouter`. Read-only commands return immediately; `run` commands dispatch through `AgentInvocationService` with `invocation_source=SLACK_*` and HITL-gated steps render approval cards in the configured channel.

| Command | Tool | Notes |
|---|---|---|
| `/orbit alerts [critical\|warning\|info]` | `orbit.get_alerts` | Top 10 open alerts by severity |
| `/orbit bugs [p0-p3]` | `orbit.get_bugs` | P0/P1/P2/P3 + SLA breach summary |
| `/orbit crs` (project inherited from thread) | `orbit.get_crs` | Top 10 open CRs · project-scoped when `projectName` resolves |
| `/orbit briefing` | `orbit.get_briefing` | Severity-dotted bullets aggregated from alerts + P0 bugs + open CRs |
| `/orbit forecast <project>` | `orbit.get_forecast` | Burn %, exhaustion date, rate from latest `ManDaySnapshot` |
| `/orbit capacity` | `orbit.get_capacity` | Per-team utilisation grouped from `Developer` rows |
| `/orbit report` | `orbit.get_report_status` | Latest 5 `GeneratedReport` rows |
| `/orbit run <report\|forecast <project>\|briefing>` | `orbit.run_*` | ADMIN/PJM only (`SLACK_INVOKE_ROLES`); HITL steps DM the approver |
| `/orbit snapshot` | `SnapshotSlackHandler` | Opens a 3-select modal (portfolio · lens · project); DMs back a stable `/snapshots/{id}` link that doubles as the progress tracker — see Snapshot Reporting Agent below |
| `/orbit-link <email>` | n/a | Magic-link DM (15-min TTL) confirmed at `/slack/link?token=` |

Threads carry short-term memory (`SlackConversationStore`, `memory_type='SLACK_THREAD'`, 60-min TTL) so follow-ups inherit `projectName` / `severity`. Result cards append 1–2 "next action" buttons that dispatch through the same pipeline. Full sequencing and per-phase scope live in [`slack-agent-implementation-plan.md`](slack-agent-implementation-plan.md).

#### Snapshot Reporting Agent *(new — 2026-06-28)*

`/orbit snapshot` captures the live Radar/Orbiter page filtered by portfolio · lens (role) · project and returns a stable shareable link. The same link is the progress tracker — no `chat.update` bridge, no separate Slack status helper.

```
Slack ──/orbit snapshot──▶ SnapshotSlackHandler ──views.open──▶ Block Kit modal (portfolio·lens·project)
                                       │
                                       └─submit──▶ SnapshotService.request(user, args)
                                                       │
                                                       ├─ cache hit (5-min TTL on READY)  ─┐
                                                       ├─ dedup hit (partial unique index) ─┼─▶ DM with /snapshots/{id}
                                                       └─ fresh: insert PENDING + @Async ──┘
                                                                       │
                                                                       ▼
                                                       RadarSnapshotAgent → SnapshotJwtService (5-min, scope=snapshot:read)
                                                                       │
                                                                       ▼
                                  HttpSnapshotRendererClient ──POST /render──▶ snapshot-sidecar (Node 20 + Playwright)
                                                                       │           goto(target?snapshot=1&token=…)
                                                                       │           waitForSelector([data-snapshot-ready="true"])
                                                                       │           ◀── { png:b64, pdf:b64, renderMs }
                                                                       ▼
                                  SnapshotStorageService → /var/snapshots/{id}/{png,pdf}, state=READY, completed_at
                                                                       │
                          Frontend SnapshotViewerPage (/snapshots/:id) ─┴─polls /status (1.5s, → 5s backoff)
                                                       ▼
                                          PNG / PDF download buttons
```

**Locked design decisions:**
- **Zero divergence from the live UI.** Snapshots are literally the pixels Chromium renders when it loads `https://<orbit>/radar?snapshot=1&portfolio=…&lens=…&project=…&token=…`. Same React bundle, same design tokens, same charts. No second template, no Java-side rendering, no parallel layout. **Any change to `RadarPage.tsx` is in the next snapshot automatically.** The only contract the page owes the sidecar is `data-snapshot-ready="true"` on the outermost div once data has loaded.
- **Link-as-progress-tracker.** The viewer page polls `GET /api/v1/snapshots/{id}/status`; we never `chat.update` a Slack message. One stable URL, durable across refreshes.
- **Idempotency at the DB layer.** SHA-256 fingerprint over `(userId, portfolioId, lens, projectId, kind)` truncated to 16 hex chars, enforced by partial unique index `agent_snapshots(dedup_key) WHERE state IN ('PENDING','RUNNING')`. Duplicate submits coalesce: same user re-clicking returns the same `id`. Cache hit on recent READY (5-min TTL) skips re-rendering entirely.
- **Headless-browser sidecar.** Single new Node 20 + Playwright + Express container (`services/snapshot-sidecar/`). Wired in compose; concurrency capped (`MAX_CONCURRENT=2`).
- **Short-lived snapshot JWT** (5-min TTL, single scope `snapshot:read`) minted by `SnapshotJwtService`, passed as `?token=` URL param. Frontend `api/client.ts` recognises it only when `?snapshot=1` is also set, so the token can never escape headless-render flows.
- **Cross-role rendering by JWT elevation, not by Slack role gate.** Any linked Slack user can pick any lens (LEADERSHIP, ENGINEERING, PM, CSM, REVENUE) — that's the whole agent value. To make this safe, `JwtFilter` recognises `scope=snapshot:read` and adds `ROLE_ADMIN` to the request authorities **only on safe HTTP methods (GET)**. Mutating verbs (POST/PUT/DELETE/PATCH) run with the caller's real role, so the elevation can never be abused to write data, invoke agents, or bypass HITL. Combined with the 5-min TTL and the URL-only delivery path, the blast radius of a leaked snapshot JWT is read-only access to data the requester would have seen with admin permissions, for at most 5 minutes.
- **Frontend snapshot-mode bypasses `RequireAuth`.** Headless Chromium has no zustand `user` in localStorage; the URL JWT is the only auth. `App.tsx`'s `RequireAuth` skips the redirect to `/login` when `?snapshot=1` is present so the radar route can render.
- **Retention.** Artifacts live on disk (`SNAPSHOT_STORAGE_ROOT`) for 7 days (`expires_at` column); a swap to S3 is a one-class refactor in `SnapshotStorageService`.
- **Watchdog.** `SnapshotWatchdog` runs every 30 s and marks rows stuck in PENDING/RUNNING for > 60 s as FAILED — frees the partial-unique slot so retries aren't permanently locked out.
- **Authorization.** Status + download endpoints `@PreAuthorize("isAuthenticated()")` plus controller-side check: `snapshot.userId == auth.userId || role == 'ADMIN'`. 410 GONE on expired, 409 CONFLICT on not-yet-READY.

Schema lives at V79 (`agent_snapshots`). API surface in [`lld.md §5.18 Snapshots API`](lld.md).

#### Shared Prod-Bug Routing *(new — 2026-07-01)*

Some organisations run a single shared Jira project that holds production bugs for **every client**. The client is identified only by a custom field on the issue (e.g. `customfield_11683` → client code `ACME`, `BEETLE`, …). Orbit's default 1-Jira-project → 1-Orbit-project → 1-Client assumption doesn't hold there — per-client rollups, SLA overrides, Slack briefings, Radar KPI tiles, portfolio health, and alerting all key on Client, not on the shared source project.

```
Jira ──POST /rest/api/3/search/jql──▶ JiraSyncService
                                             │  (project.is_shared_prod_bugs=true?)
                                             │
                                             ├── no  ─▶ existing path (project.client)
                                             │
                                             └── yes ─▶ ProdBugRoutingService.route(issue, rawCode)
                                                          │
                                                          ├── code missing/blank ──▶ quarantine (MISSING_CODE)
                                                          ├── code unknown       ──▶ quarantine (UNKNOWN_CODE)
                                                          └── code → Client X    ──▶ issue.client = X; auto-clear open quarantine

Admin UI (Portfolio Setup > Prod-bug routing):
  Shared pools    — GET  /config   ·  Backfill: POST /backfill/{projectId}
  Client codes    — GET  /clients  ·  POST /clients/{id}/code
  Quarantine      — GET  /quarantine (paged) · POST /quarantine/{id}/resolve

Three entry points to mark a project as a shared pool:
  a) Portfolio setup → New project → "Shared prod-bug pool" checkbox
     (client dropdown becomes optional; custom-field ID required).
  b) Portfolio setup → expand existing project row → routing panel.
  c) Portfolio setup → Prod-bug routing → "+ Add shared pool" modal.
All three POST/PUT the same `projects.is_shared_prod_bugs` + `client_code_field`.
```

**Locked design decisions:**
- **Fan-out at ingest, not at query.** `JiraSyncService` reads `customfield_XXXXX` and sets `jira_issues.client_id` to the resolved client. `jira_issues.project_id` still points at the shared Orbit project — no schema shift on the primary key relationships. Every existing consumer that filters `(:clientId IS NULL OR j.client.id=:clientId)` is correct automatically; SQL evaluates `NULL = :clientId` as UNKNOWN so quarantined bugs never leak into per-client rollups.
- **Reuse existing `clients.code`.** No new column; V80 adds a partial unique index (`WHERE code IS NOT NULL AND code <> ''`). Lookups use `UPPER(TRIM(...))` on both sides so `Acme`, `ACME`, and `  acme ` all match one row.
- **Quarantine, don't drop.** Missing/unknown codes land in `prod_bug_quarantine` (idempotent per `jira_key`) and hold `client_id = NULL`. The bug is still synced from Jira — the source of truth — but doesn't skew any per-client aggregate until an admin resolves it. Admin-resolved rows are automatically re-opened if the bug shows up stuck again in a later sync. **No prod bug is ever silently discarded.**
- **Per-project config, not global.** `projects.is_shared_prod_bugs` + `projects.client_code_field` — multiple regions can each have their own shared pool with different custom-field IDs.
- **Global env kill-switch.** `orbit.prod-bug-routing.enabled` (default `true`, override `ORBIT_PROD_BUG_ROUTING_ENABLED`). Per-project flag stays authoritative; env just gives ops a fast off-switch.
- **Force `PROD_BUG` type inside the shared branch.** Every issue in the shared pool is a production bug by definition, regardless of Jira's issuetype string.
- **Backfill = full re-sync.** Historical rows never captured the raw custom-field value, so `ProdBugBackfillService` re-hits Jira via `JiraSyncService.trigger("full", projectId)` — the routing branch does the rest. Reuses one code path and gets quarantine seeding for free.
- **Inline "assign on resolve".** The admin can attach a client code inline while resolving a quarantine row; the linked `JiraIssue` gets its `client_id` rewritten in the same transaction so it appears in the client's rollups immediately without waiting for the next sync.
- **RBAC.** All mutations (`PUT /config`, `POST /clients/{id}/code`, `POST /quarantine/{id}/resolve`, `POST /backfill/{projectId}`) are `ADMIN`-only via `@PreAuthorize`. Reads (`GET /config`, `GET /clients`, `GET /quarantine`) also allow `HEAD_PJM`.

Schema lives at V80 (`clients.code` partial unique index · `projects.is_shared_prod_bugs` + `projects.client_code_field` · `prod_bug_quarantine`). Service layer: `ProdBugRoutingService` + `ProdBugBackfillService`. Admin surface: `POST/GET /api/v1/admin/prod-bug-routing/*`.

### BandViz (existing)
- Orbit merges BandViz into same Spring Boot deployable
- Capacity, leave, assignment data accessed via shared DB schema (V16+ Flyway migrations)

### Notifications
- Email via SMTP/SendGrid · Slack via Bot API · Teams via Connector
- All optional, configured per alert type and severity
- **All require HITL approval before sending** (HITL Gateway — no exceptions)

### AI Providers
- OpenAI (or Anthropic) via env var `AI_PROVIDER` — model swappable at deploy time
- All LLM calls routed through `AiGatewayService` for rate limiting, retry, cost logging
- Per-run token count stored in `agent_runs.tokensUsed`
- Weekly cost aggregate surfaced in Agent Audit Log header

### Prophet Forecast Sidecar
- Python 3.11 + Prophet + FastAPI — separate process, same Docker Compose network
- Called by ManDayForecastAgent via REST `POST /forecast`
- Returns: `forecast: ForecastPoint[]` with `ds`, `yhat`, `yhat_lower_80`, `yhat_upper_80`, `yhat_lower_95`, `yhat_upper_95`
- CI bands rendered as shaded polygons on Man-Days burn chart

---

## 10. Alerts & Notifications Engine *(v5.0 — Phase 1 & 2)*

Delivers proactive delivery reminders and automated escalations via Slack.

### 10.1 Phase Status Tracking

Each project has up to 5 phases (FSD · DEV · QA · UAT · PROD). Each phase stores:
- Start date / end date
- Assignee email + name (used for Slack DM resolution)
- Status: `NOT_STARTED` · `IN_PROGRESS` · `ON_TRACK` · `DELAYED_SELF` · `DELAYED_SYSTEM` · `COMPLETED`
- Notification tracking: `lastNotifiedT2`, `lastNotifiedT1`, `ddayNotified`

Phase statuses are managed by Admins/PJMs via the **Phase Deliveries** admin page, and will be synced from Jira custom fields in Phase 4.

### 10.2 Notification Scheduler

`NotificationSchedulerService` runs every 30 minutes (`@Scheduled(fixedDelay=30*60*1000)`).

For each active phase (all phases where `end_date IS NOT NULL` and `status != COMPLETED`):

| Condition | Action |
|---|---|
| `today == end_date - 2` and T-2 not yet sent | Send T-2 reminder DM to assignee via Slack |
| `today == end_date - 1` and T-1 not yet sent | Send T-1 reminder DM to assignee via Slack |
| `today == end_date` and D-Day not yet notified | Send D-Day check-in DM to assignee |
| `today > end_date` and status not `DELAYED_SYSTEM` | Set status → `DELAYED_SYSTEM`, create overdue `Alert`, send escalation to project Slack channel |

Deduplication: each event type is checked against `notification_events` table — no re-send within 23 hours for the same phase + event type.

### 10.3 Escalation Chains

Two-layer escalation per role/phase (configurable in Admin):
- **Layer 1 — Phase SPOC:** Role-specific contact (e.g. Tech Lead for Developer delays, Engineering Manager for TL delays)
- **Layer 2 — Delivery SPOC:** Universal escalation contact notified on every phase miss across all projects

Global SPOCs (Delivery SPOC, Solutions SPOC, Engineering Manager) are configured once in the Global SPOCs admin tab.

### 10.4 Notification Rules (23 seeded)

Pre-seeded rules covering T-2, T-1, D-Day for: Developer (DEV), Tech Lead (DEV + PROD), QA Lead (QA), Project Manager (UAT), Solution Manager (FSD). Plus 4 daily digest rules for Tech Lead, Eng Manager, Delivery SPOC, Solutions SPOC. All rules are enable/disable toggleable from the Notification Rules admin page.

### 10.5 Alert Integration

Overdue phases create `Alert` records with:
- `alertType = "PHASE_OVERDUE"`
- `severity = "critical"` (≥3 days late) or `"risk"`
- `phase` column (new in v5.0) — e.g. `"DEV"`, `"QA"`
- `daysOverdue` column (new in v5.0) — shown as `Nd late` badge in Alert Center

### 10.6 Roadmap (Phase 3 & 4)

| Phase | Scope |
|---|---|
| Phase 3 | Interactive Slack D-Day prompts (Block Kit buttons: On Track / Delayed) · EOD completion prompt · Overdue loop (every 3h 09:00–21:00) · Daily digest |
| Phase 4 | Jira write-back (phase status + delay note → custom fields) · Jira field mapping UI in Integrations > Jira tab |

---

## 11. Non-Functional Requirements

| Attribute | Target |
|---|---|
| Webhook processing latency | < 10 seconds end-to-end |
| Dashboard page load | < 2 seconds (Redis cached) |
| Report generation | < 30 seconds (streamed WS completion event) |
| Jira delta sync interval | Every 10 minutes |
| LLM agent response (first token) | < 3 seconds |
| LLM agent response (complete) | < 8 seconds |
| Agent run duration (p95) | < 30 seconds (excludes HITL wait time) |
| Concurrent internal users | 50 (initial deployment) |
| Data retention | 24 months of snapshots |
| Uptime (internal tool) | 99% business hours |
| RBAC route enforcement | All screens server-side `@PreAuthorize` + client-side route guards |

---

## 12. Open Decisions — Resolved (v3.0 + v4.0 + v5.0)

All 4 open decisions from alignment review were resolved in v3.0 and remain resolved.

**Decision 1 — UAT as standalone screen:** Resolved as standalone. UAT Tracker is a first-class screen (EPIC-05). Bug Triage UAT tab is retained as a quick entry point but links through. Standalone screen supports cycle management, sign-off tracking, UAT environment health per client.

**Decision 2 — Report schedule admin location:** Resolved as a "Schedules" tab within the Reports screen for PJM access, plus an Admin > Report Schedules screen for admin configuration (recipients, automation rules). Both reference the same `report_schedules` table.

**Decision 3 — Man-day budget edit RBAC:** Resolved as view-only for PM by default; edit for ADMIN always, and for PM users whose `AppUser.canEditBudget` DB column is `true` (exposed as JWT `canEditBudget` claim). `PUT /api/v1/man-days/budget` checks this claim server-side. Edit Budget modal only renders for qualifying users.

**Decision 4 — Client health thresholds:** Resolved as per-client, configurable. Default thresholds: green ≥80, amber ≥60. Admins can override per client in Admin > User Management or Client Backlog admin panel. Health score colour reads from `clients.health_green_threshold` and `clients.health_amber_threshold` at render time.

**v4.0 — Agent Builder scope:** New agents created via Agent Builder are stored in `agent_definitions` and executed by the same AgentRuntime as built-in agents. Built-in agents (original 6) cannot be deleted from the UI but can be disabled. Custom agents can be fully deleted only if they have no run history.

---

## 12A. v10.0 Capability Additions

### Multi-client portfolios (Portfolio ↔ Client M:N)

| Entity | Relationship | Cardinality |
|---|---|---|
| Portfolio ↔ Client | `portfolio_clients` join table | **M:N** *(was 1:N in ≤v9.0)* |
| Project → Client | `projects.client_id` FK | **1:1** *(unchanged)* |
| Portfolio → Project | `projects.portfolio_id` FK | 1:N |

A portfolio can span multiple clients (e.g. multi-tenant POD). Each project inside a portfolio still belongs to exactly one client. UI lets admins multi-select clients in the Portfolio Setup → Edit modal; the "New project" flow scopes the client dropdown to the portfolio's linked clients.

API: `clientIds: number[]` on POST/PUT `/portfolios`; response carries `clientIds[]` + `clientNames[]` plus single-value `clientId`/`clientName` (first entry) for back-compat.

### Project life-stage health scoring

Stage is auto-inferred from `projects.go_live_date` or manually overridden via `projects.health_stage`:

| Stage | Condition | Dominant signals |
|---|---|---|
| `PRE_LAUNCH` | `go_live_date` null or future | CR-on-hold %, UAT bug count, manday burn risk |
| `HYPERCARE` | ≤ 90 days since `go_live_date` | P0/P1 bugs, SLA breach |
| `STEADY_STATE` | > 90 days since `go_live_date` | Balanced — bugs, CR holds, manday burn |
| `AT_RISK` | Manual flag (admin/CSM only) | All signals elevated |

`ProjectHealthService.compute(project)` returns `(healthPct, stage, signals[])`:
- `signals[]` lists each metric's raw value, normalised value (after sensitivity), weight, and resulting deduction
- `healthPct = clamp(100 − Σ(normValue × weight), 0, 100)`
- Per-stage weights live in `health_profile_weights`, editable by admin

POD health on the radar = average of constituent project scores. RAG threshold: Red <50, Amber <75, Green ≥75.

### SLA rules engine

- **Defaults** seeded for P0/P1/P2/P3 (`response_hours` = at-risk threshold; `resolution_hours` = breach threshold)
- **Client overrides** — same composite key (severity, client_id); resolution: client override → global default
- **`SlaService.computeStatus()`** — returns `On track` / `At risk` / `Breached`; respects `include_weekends` flag (business hours Mon–Fri 9–18 IST otherwise)
- **`@Scheduled(cron = "0 0 * * * *")` `recomputeAll()`** — refreshes all open bugs hourly
- **Jira JSM integration** — if `jira_config.sla_field` is set, `SlaService.parseJiraSlaStatus()` reads `ongoingCycle.remainingTime.millis` / `breached` from the field on every sync; otherwise computed from rules

### HRMS connector factory (Darwinbox as reference provider)

HR integration is provider-pluggable (`connector/hrms/`): each provider implements
`HrmsConnector` (providerKey · displayName · settings descriptor · testConnection ·
sync · webhook processing) and registers as a Spring bean; `HrmsConnectorFactory`
collects them by key. `HrmsSyncService` resolves the single `hrms_config` row
(`provider_key` + JSONB `settings` + `enabled`, V91), owns `hrms_sync_runs`
bookkeeping and the scheduled delta sync, and is a graceful no-op when no HRMS is
configured — capacity/leave views simply show whatever data exists. Adding a
provider (Keka, BambooHR, Workday, …) is one class + one bean; the FE "HR System"
card renders its settings form from the connector's descriptor, so no UI changes.

Darwinbox (`connector/hrms/darwinbox/DarwinboxHrmsConnector`):

Auth modes selected via the `authType` setting:
- `API_KEY` — `x-api-key` header *(most common Darwinbox setup)*
- `BEARER` — `Authorization: Bearer {key}` header
- `HMAC` — header path stubbed; signing not implemented

Sync orchestration (`sync(settings, type)`):
1. **Employee directory** (`GET /apiv2/employees`, paginated) — auto-maps Orbit users to Darwin employee IDs by email
2. **Per mapped user**:
   - Leave records (`POST /apiv2/employees/leavedetails`)
   - WFH records (`POST /apiv2/employees/wfhdetails`)
   - Leave balances (`POST /apiv2/employees/leavebalance`)
   - Attendance (`POST /apiv2/employees/attendance`, paginated)

All responses are unwrapped from `{ status: true, data: [...] }`. Field names mapped from snake_case (`leave_id`, `from_date`, `no_of_days`, `wfh_date`, `check_in`).

Webhook receiver: `POST /api/v1/hrms/webhook` (permitted without auth; HMAC-SHA256
over the raw body against the `webhookSecret` setting, signature header declared by
the connector — Darwinbox uses `X-Darwin-Signature`). Handles `leave_applied`,
`leave_approved`, `leave_cancelled`, `wfh_applied`, `wfh_approved`, `wfh_cancelled`,
`employee_updated`, `employee_created`.

### Google SSO

Endpoints (added to `AuthController`):
- `GET /api/v1/auth/google` → builds Google OAuth2 redirect URL with `client_id`, `redirect_uri`, `scope=openid+email+profile`
- `GET /api/v1/auth/google/callback?code=…` → exchanges code for `id_token`, calls `tokeninfo`, verifies email, upserts `AppUser` (default role: PM), mints Orbit JWT, redirects to `${FRONTEND_URL}/login?token=…&id=…&role=…`

Frontend `LoginScreen` parses URL params on mount, calls `onLogin`, and navigates to `/radar`.

Gracefully falls back when `GOOGLE_CLIENT_ID` is unset — `?error=google_not_configured` flag.

### Jira issue-type mapping (rule change)

`JiraSyncService.mapIssueType(jiraType)`:

| Jira type | Orbit type |
|---|---|
| `Bug` · `bug` · `UAT Bug` · `Defect` · `UAT Defect` | **`UAT_BUG`** |
| `Production Bug` · `Prod Bug` · `Production Defect` | **`PROD_BUG`** |
| anything else | `CR` |

**Rationale**: project bugs are UAT defects discovered during the delivery cycle. PROD_BUG is reserved for explicitly-tagged production incidents from live workflow.

V68 reclassifies legacy `PROD_BUG` rows whose `jira_status` doesn't indicate a real production incident (heuristic: status NOT LIKE `%production%` / `%hotfix%` / `%outage%` / `%sev-1%`).

### Bug filters (UAT Tracker + Bug Triage)

`BugController` accepts and propagates: `clientId`, `severity`, `slaStatus` (prod), `stage` (UAT). Repository null-safe queries (`countOpenByClientTypeAndSeverityIn`, `countOpenReopenedByClientAndType`, `countOpenUnassignedByClientAndType`) replace the broken derived methods that filtered `WHERE client_id = 0`. All summary tiles now show real counts across all clients when no filter is set.

Frontend dropdowns are now `value`/`onChange`-controlled, with the client list loaded from `/clients`. URL deep-link via `?severity=P0&clientId=42` on `/bugs`. Per-tab filter rows (Production: severity + SLA state; UAT: stage). "Clear filters" button when any non-default filter is active.

---

## 12B. v10.1 Capability Additions — Account Detail

### Per-account deep-dive page

Click any account card on Orbitter → routes to `/accounts/:projectId` → calls **`GET /api/v1/accounts/{projectId}`** (single aggregator) and renders a mock-faithful detail page.

**Sections (in render order)**:

| Section | Source | Notes |
|---|---|---|
| Sticky header | `projects`, `ProjectHealthService` | Name · stage badge · RAG badge · health % · client·POD·go-live line · ops-model dropdown · Export PDF |
| Weekly status | `ProjectHealthService` + counts | % completion + computed bullets (open CR/bug counts, current stage) |
| Mandays strip (3 cards) | `man_day_budgets` + latest `man_day_snapshots` | Total · Consumed (with progress bar + RAG colour) · Remaining (warning when <15% buffer) |
| Project timeline & delivery health | `jira_issues` by lifecycle stage | 2-column: Launch ops (CR workflow stats + dates) · BAU ops (PROD_BUG workflow stats + last UAT sign-off / go-live) |
| Risk register | `project_risks` (V69) | DB-backed; inline add form (jira ticket · risk text · received date · RAG · action end · owner · source); delete per-row; role-gated to PM/ADMIN/CSM |
| Production issues tracker | `jira_issues WHERE issue_type='PROD_BUG'` | Total open · S1–S4 by severity · avg ageing · closed count · status breakdown · ageing buckets {0-30, 31-90, 91-180, 180+} · issue rows |
| Release calendar | `project_releases` (V69) | Date range = today − 14d → today + 2mo; cards coloured by type (launch/bau/support) |
| 3-column worklists | `jira_issues` filtered by type+stage | Launch stories (CRs in "In dev") · Open CRs (non-closed) · Production bugs (non-closed) |
| Workbench → Team | `project_team` (V69) | 4 internal + 4 client contact cards; editable via `PUT /accounts/{id}/team` |
| Workbench → Health | Derived from `ProjectHealthService.signals` + bug counts | Sentiment (score = healthPct/10, top-3 deductions as reasons) · Delivery speed · Platform stability · Open UAT bug table |

### Aggregator contract

`AccountDetailService.assemble(projectId)` orchestrates 7 repositories in a single transaction-equivalent call and returns one JSON payload. Frontend uses a single `useQuery(['account-detail', projectId])` — no chatty per-section calls.

### Ops model

`projects.ops_model` (`launch` | `bau` | `launch+bau`) — captured per project; influences which timeline blocks (Launch / BAU / both) are emphasised in the UI. Editable via `PUT /accounts/{id}/ops-model` (PM/ADMIN).

### PDF export

`window.print()` + `@media print` CSS that hides tab switchers, action buttons, and add/delete controls (selector class `no-print`). Mock-equivalent — no backend PDF generator dependency.

### Click-through

`RadarPage` account cards: `onClick={() => navigate('/accounts/' + acct.id)}` (replaces previous `/cr?clientId=…` deep-link).

---

## 12C. Capability Additions — Controlled Release, Composite DS Layer, Responsive Shell *(2026-07-08)*

Frontend conventions: [`frontend-component-strategy.md`](frontend-component-strategy.md).

### Controlled release (feature flags)

Standalone flag system for piloting — deliberately **not** an extension of `role_screen_config` (see §6 note). One table (`feature_flags`, V81): `flag_key` · `audience` (`ALL`/`PILOT`/`NONE`) · `pilot_emails JSONB` · audit columns.

- **Unknown key = visible.** Flags exist only for features being held back; deleting a flag releases the feature. No registry of every component.
- **Key convention:** `screen.<navId>` gates a sidebar item *and* its route (direct URL redirects to `/radar`); `section.<page>.<name>` gates a component via `<Feature flag="…">`.
- **ADMIN bypass:** admins resolve every flag to `true` so they can verify before widening the audience.
- **Pilot workflow:** ship at `NONE` → flip to `PILOT` + emails → widen to `ALL` → delete the flag.
- Frontend resolves once per session via `GET /api/v1/feature-flags/effective` (react-query, 60s stale). Trade-off: flags load async, so a held-back section can flash briefly on first paint — accepted for an internal tool.
- Managed in the **Features Control Center** (`/flags`, ADMIN-only, screen 19).

### Composite design-system layer

The atom layer (24 components) is now complemented by composites in `design/components/`: `PageHeader` · `StatGrid` · `FilterBar` · `TableWrap` · `StatusPill` (single status→tone source of truth — page-local colour maps are banned). Pages compose these instead of hand-rolling headers/stat rows/filter rows/table chrome. **Reference implementation: Alert Center** (`features/alerts/AlertsPage.tsx`); remaining screens migrate opportunistically via the checklist in `frontend-component-strategy.md`.

### Responsive shell (WebView-ready)

Breakpoints `mobile <640px / tablet <1024px / desktop` via `design/useBreakpoint.ts`. As of v10.4 the shell is a single responsive `TopBar` at every width (the sidebar/hamburger-drawer split is gone); the document scrolls naturally (same layout snapshot mode uses — the WebView-friendliest structure for a future mobile wrapper app). Composites are responsive by construction, so screens inherit mobile behaviour by adopting them. Copilot panel is a fixed FAB on all widths. Auth is header-JWT (WebView-safe); Google SSO inside a WebView is a flagged future test item.

## 12D. Capability Additions — AM Dashboard V3, Dark-Indigo Theme, Client Master Page *(2026-07-14)*

Supersedes the teal/sage palette from the V2 mock.

- **Theme:** dark-indigo (`#5b7cfa`) is the default theme, from the approved V3 mock; light is the derived indigo palette. Green/amber/red are RAG-only. Snapshot/report renders always light (`?snapshot=1`).
- **AM Orbitter home (V3 widgets):** POD Benchmarking scored by SLA adherence vs absolute stage targets (interim until CSAT — the mock's 60/40 CSAT+SLA blend lands in Phase D), page-level POD selector scoping every section, Client Scorecard tiles that open the client master page, Production Tickets with quarter/custom windows + week-on-week drill, transposed Delivery Stage matrices (clients as rows, SLA % in stage column headers), owner-share donuts (top-9 + Others). Metric ⓘ popovers carry the mock's full definitions (`features/radar/am/metricInfo.ts`).
- **Client master page** (`clients/:clientId`, all roles): tabs Overview · Delivery Speed · Quality · Predictability · Teams · Workbench. Real-now metrics: lead time, throughput, stage-SLA compliance, production incidents, backlog aging; the nine metrics needing changelog/sprint/deploy feeds ship behind `section.client.dh.*` flags at `NONE` (V83) with honest awaiting-feed cards. Pillar scores are RAG-banded v1 (92/62/32); weights admin-configurable (Speed 40 · Quality 35 · Pred 25).

---

## 13. Sprint Delivery Plan (v5.0)

| Sprint | Scope |
|---|---|
| **S1 (2w)** | DB schema V16–V24 · Jira sync (webhook + delta) · CR/bug API · basic React shell + sidebar (collapsible) + CR board |
| **S2 (2w)** | Alert engine (with follow-up date) · Man-day consumption + budget edit · Man-Days screen · Leadership Radar (heuristic scores) |
| **S3 (2w)** | JWT auth + RBAC guards · Bug Triage (Prod + UAT tab) · PJM Cockpit action list · StandupAgent (v1 with countdown) |
| **S4 (2w)** | DeliveryIntelligenceAgent · WebSocket copilot streaming · HITL approval flow · pgvector + embeddings · UAT Tracker standalone screen |
| **S5 (2w)** | ManDayForecastAgent (Prophet sidecar + CI bands) · EscalationAgent · ReportDraftingAgent · Report Schedules admin · .docx/.pdf export |
| **S6 (2w)** | AI executive briefing (LLM) · BERT triage auto-tag · Slip probability v2 (LLM-augmented) · Agent Audit Log with cost tracking · Client Backlog dependency tracker |
| **S7 (1w)** | Load testing · security review · Swagger docs · deployment runbook · handover |
| **S8 (2w)** | Agent Framework: V49–V53 migrations · ToolRegistry · AgentRuntime · HITL Gateway refactor · SlackService · AgentDefinition CRUD API · Integrations API · DevReminderAgent · Agent Builder screen (ADMIN) · Integrations screen (ADMIN) · ClientUpdateAgent (skeleton) |
| **S9 (2w)** | Notifications Engine (Phase 1 & 2): V54–V59 migrations · NotificationSchedulerService · AlertRulesController + PhaseStatusController · SlackService.sendToChannelDetailed() · SlackSendChannelTool channel fix · AgentRuntime @JdbcTypeCode fix · Notification Rules + Phase Deliveries admin UI · Integrations page consolidated · Alert Center phase column · **Agent Logs + HITL Approvals**: V60 migration · HitlApprovalService (approve/reject: executes tool + writes agent_decision_log) · AgentLogsController (GET /runs, GET /runs/pending-hitl, GET /runs/{runId}/steps, POST .../approve, POST .../reject) · AgentDecisionLog @JdbcTypeCode fix | Integrations page (Jira + Darwinbox + Slack tabs) · Notification Rules admin (4 tabs) · Phase Deliveries admin · Alert Center phase column · **AgentLogsPage** (/agent-logs — Execution logs + Pending Approvals tabs · per-step expand · approve/reject/reject-with-reason HITL flow) · **AgentRunHistory** component (inline run history in Agent Builder, reused in AgentLogsPage) · Agent Builder: "▾ Runs" toggle per agent |
| **S10 (1w)** | **Security hardening + WFH sync**: JWT → RS256 · Jira webhook dev-bypass removed · Slack token AES-256 encryption · CORS env-var allowlist · Copilot hardcoded proposal removed · Real SLA breach counts · AgentRuntime tool args passthrough · StandupAgent countdown fix · 16 missing agent tools implemented · DevReminderAgent · V61 `wfh_records` migration · `DarwinboxConnectorService` WFH sync (`/apiv2/employees/wfhdetails`) · `GET /api/v1/darwin/wfh` endpoint · `orbit.get_leave_today` + `orbit.get_upcoming_leaves` return WFH data | Darwinbox **WFH data** tab (5th tab) · "WFH this week" stat card |
| **S11 (patch)** | V62 `can_edit_budget` on `app_users` · JWT `canEditBudget` claim · budget-edit permission check in `ManDayController` · `portfolioId` filter on CR list + stage-summary · `GET /portfolios/{id}/summary` · `GET /man-days/portfolio-summary` · `JiraIssueRepository` portfolio queries · role rename PM/ENGINEERING throughout | `GlobalCopilotPanel` floating global copilot (Shell-level) · Login tagline "Delivery Command Center" · Sidebar: Orbitter home (no My Today/cockpit) · No Jira Live section |
| **S12 (v10.0)** | **Multi-client portfolios**: V67 `portfolio_clients` M:N join table · `Portfolio.clients : Set<Client>` · `findByClientsIdAndActiveTrue` · `clientIds[]` request/response field with single-field back-compat. **Project health scoring**: V66 `projects.go_live_date`, `projects.health_stage`; `health_profile_weights` (4 stages × 6 metrics seeded); `ProjectHealthService.compute(project)` returns `(healthPct, stage, signals)`; auto-stage inference (null/future → PRE_LAUNCH, ≤90d → HYPERCARE, 91+ → STEADY_STATE; AT_RISK manual). **SLA engine**: V64 seeds P0–P3 defaults; `SlaService.computeStatus(severity, createdAt, client)` with client override → global default; hourly `@Scheduled` recompute; optional JSM `customfield_*` SLA read on sync. **Darwinbox full**: V65 `darwinbox_config` (encrypted at rest), `attendance_records`, `leave_balances` tables; `AuthType` enum (`API_KEY` / `BEARER` / `HMAC`); employee directory sync auto-maps by email; pagination loop in all fetches; response envelope `{ data: [...] }` unwrap; snake_case → camelCase mapping; `POST /api/v1/darwin/webhook` receiver. **Google SSO**: `GET /auth/google` redirect; `GET /auth/google/callback` exchanges code → fetches profile → upserts user → mints Orbit JWT. **Jira bug mapping**: project bugs default to `UAT_BUG`; only "Production Bug"/"Prod Bug"/"Production Defect" → `PROD_BUG`; V68 reclassifies legacy `PROD_BUG` rows. **Bug filter fix**: `BugController` accepts clientId/severity/slaStatus/stage; null-safe count queries replace derived methods (`countOpenByClientTypeAndSeverityIn`, `countOpenReopenedByClientAndType`, `countOpenUnassignedByClientAndType`). **Package rename**: `com.gauge.*` → `com.orbit.*`; default password `gauge123` → `orbit123` for fresh installs. | Multi-select `ClientPicker` in Portfolio setup (Add/Edit modals) · inline "New project" modal with portfolio-scoped client dropdown · Account cards with stage badge + health bar (Red <50, Amber <75, Green ≥75) · POD Health KPI tile · Health profiles admin page (Pre-launch/Hypercare/Steady-state/At-risk tabs · weight slider + sensitivity field per metric · total deduction preview) · SLA rules page with default + override sections · Darwinbox admin form (DB config + auth type + webhook URL display) · Bug Triage filter dropdowns wired (client + severity + SLA status; UAT stage) · URL `?clientId=`/`?severity=` deep linking · Favicon (inline SVG orbit logo) · Reactive sidebar collapse (individual `useStore` selectors) · "Open Prod Bugs" tile label · CSS custom-property dark mode (`[data-theme="dark"]`) · Google SSO button wired |
| **S13 (v10.1)** | V69 migration: `project_team` (4 internal + 4 client contact columns) · `project_risks` (jira_ticket · risk · received_on · rag · action_end · action_owner · source) · `project_releases` (release_date · release_type · label · rag) · `projects.ops_model` column. New entities `ProjectTeam`, `ProjectRisk`, `ProjectRelease` + 3 repositories. **`AccountDetailService`** aggregator orchestrating 7 repos (mandays/milestones/weeklyStatus/launchOps/bauOps/productionIssues/4 worklists/team/health/risks/releases). **`AccountDetailController`** with 5 endpoints: `GET /accounts/{id}` (aggregator · authenticated); `PUT /accounts/{id}/team` (PM/ADMIN); `POST /accounts/{id}/risks` + `DELETE /accounts/{id}/risks/{id}` (PM/ADMIN/CSM with cross-project guard); `PUT /accounts/{id}/ops-model` (PM/ADMIN · `launch` / `bau` / `launch+bau`). `JiraIssueRepository.findByProjectIdAndIssueTypeOrderByUpdatedAtDesc` for worklist sourcing. `AccountDetailControllerTest` (10 tests). | **`AccountDetailPage`** at `/accounts/:projectId` — sticky header (stage badge · RAG · health % · ops-model dropdown · Export PDF) · Overview tab (weekly status with 6 milestone cards · mandays 3-card strip · launch/BAU ops split · risk register with inline add form + delete · production issues tracker · release calendar · 3-column worklists) · Workbench tab (Team sub-tab with 8 contact cards · Health sub-tab with sentiment/delivery/stability cards + UAT bug table). `@media print` hides `.no-print` elements. `RadarPage` account cards rewired to navigate to `/accounts/{id}`. New `/accounts/:projectId` route added to `Shell.tsx`. |
