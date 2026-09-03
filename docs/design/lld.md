# Orbit — Low-Level Design (LLD)

**Version:** 10.1 | **Date:** June 2026 | **Stack:** React 18 · Java 25 · Spring Boot 3.2 · PostgreSQL 16  
**Change from v10.0:** Account Detail deep-dive page (`/accounts/:projectId`) backed by `AccountDetailService` aggregator + `AccountDetailController` (5 endpoints) · V69 schema: `project_team`, `project_risks`, `project_releases` tables + `projects.ops_model` column · `JiraIssueRepository.findByProjectIdAndIssueTypeOrderByUpdatedAtDesc` · `AccountDetailControllerTest` (10 tests) · Click-through from Orbitter account cards rewired from `/cr?clientId=…` to `/accounts/:id` · PDF export via `window.print()` + `@media print` `.no-print` class.

**Change from v9.0 → v10.0** *(historical)*: Multi-client portfolios (`portfolio_clients` M:N) · Project life-stage health scoring · SLA rules engine · Darwinbox full integration · Google SSO · Jira issue-type rule project bugs default to `UAT_BUG` · `BugController` null-safe filter queries · backend package rename · Migrations V64–V68.

**Change from v8.0 → v9.0** *(historical)*: Role rename · `canEditBudget` JWT claim · `GET /man-days/portfolio-summary` · `portfolioId` filter on CR list + stage-summary · `GET /portfolios/{id}/summary` · V62 migration · `GlobalCopilotPanel` (Shell-level floating copilot) · Login tagline "Delivery Command Center" · Sidebar: Orbitter home, no My Today/cockpit.

---

## 1. Tech Stack

| Layer | Technology | Notes |
|---|---|---|
| Frontend | React 18 + TypeScript + Vite | Custom design system — no MUI/shadcn; dark-indigo tokens |
| State management | Zustand | `collapsed: boolean` sidebar state · `activeScreen` · `user` session |
| HTTP client | Axios + React Query (TanStack) | 2-min TTL dashboard · 30-sec TTL CR/bug tables · optimistic updates with rollback |
| WebSocket | SockJS + STOMP | Copilot streaming · report generation progress · standup countdown |
| Backend language | Java 25 | Virtual threads (Project Loom) for agent concurrency |
| Framework | Spring Boot 3.2.x | |
| ORM | Spring Data JPA + Hibernate 6 | |
| Database | PostgreSQL 16 + pgvector extension | Primary DB + vector embeddings + agent long-term memory |
| Cache | Redis 7 (Lettuce client) | Dashboard data · session · rate limiting · standup countdown · agent short-term memory (7d TTL) |
| Migrations | Flyway | V16–V53 building on BandViz V15 |
| Auth | Spring Security + JWT (jjwt) | RS256 · 15-min access token · 7-day refresh |
| Agent runtime | AgentRuntime (custom) + Spring AI 1.x | ToolRegistry · HITL Gateway · memory integration |
| LLM client | Spring AI OpenAI/Anthropic adapter | Swappable via `AI_PROVIDER` env var |
| Slack client | Slack Web API (Bot Token) | SlackService wraps HTTP calls; token stored encrypted in `slack_config` |
| Forecast sidecar | Python 3.11 + Prophet + FastAPI | REST endpoint called by ManDayForecastAgent |
| API docs | Springdoc OpenAPI 2.x | Swagger UI at `/swagger-ui.html` |
| Build | Maven | |
| Containerisation | Docker + Docker Compose (dev) · Kubernetes (prod) | |

---

## 2. Frontend Architecture

### 2.1 Project Structure

```
src/
├── app/
│   ├── App.tsx                    # Root — router + auth guard
│   ├── router.tsx                 # React Router v6 routes with role guards
│   ├── featureFlags.tsx           # 2026-07-08: useFlags/useFlag/flagOn + <Feature> gate (see §5.20)
│   └── store.ts                   # Zustand global store
│       # State: { user, collapsed, activeScreen, pendingProposals,
│       #          roleChartConfigs, chartTypeOverride (persisted) }
├── lib/
│   └── datetime.ts                # 2026-09-03: single date-render module — configureDatetime/
│       #  initDatetime (one-shot /app/config fetch, defaults on failure),
│       #  parseServerDate (ISO-with-offset or server-zone naive),
│       #  fmtDate "25 Jul 2026" · fmtTime "14:30" · fmtDateTime ·
│       #  fmtDateTimeFull · relTime ("just now"/"12m ago" → fmtDateTime)
├── design/
│   ├── tokens.ts                  # Color, spacing, typography tokens
│   │   # C.navy, C.indigo, C.canvas, C.red, C.amber, C.green, etc.
│   ├── useBreakpoint.ts           # 2026-07-08: mobile <640 / tablet <1024 / desktop + useIsMobile()
│   ├── agentColorMap.ts           # Per-agent brand colours for audit log
│   │   # DeliveryIntelligenceAgent→indigo, StandupAgent→green,
│   │   # DevReminderAgent→amber, ClientUpdateAgent→blue, etc.
│   └── components/
│       ├── Badge.tsx              # level: critical|risk|watch|healthy|info|blue|teal|neutral
│       ├── BurnBar.tsx            # Progress bar with red/amber/green threshold
│       ├── HeatStrip.tsx          # 14-day risk trend — 3px bars, colour-coded
│       ├── StatCard.tsx           # KPI tile with icon, value, sub-label
│       ├── Pagination.tsx         # Page<T> navigation — prev/next + page count
│       ├── Modal.tsx              # Centred overlay with title + close
│       ├── Tabs.tsx               # Underline tab strip
│       ├── AiSuggestionChip.tsx   # "Suggested by AI: P1 · Assign Kavya T." — accept/override
│       ├── TypingIndicator.tsx    # Animated dots while WS tokens stream
│       ├── CostSummaryBar.tsx     # "X tokens this week · Est. $Y.ZZ"
│       │   # ── composites (2026-07-08 — see docs/design/frontend-component-strategy.md) ──
│       ├── PageHeader.tsx         # title + subtitle + actions row; stacks on mobile
│       ├── StatGrid.tsx           # auto-fit StatCard row — replaces repeat(4,1fr) grids
│       ├── FilterBar.tsx          # wrapping row of filter controls
│       ├── TableWrap.tsx          # card chrome + overflow-x for tables; footer slot for <Pagination>
│       └── StatusPill.tsx         # single status→tone map — page-local colour maps are banned
├── features/
│   ├── radar/                     # Portfolio Radar (Leadership)
│   │   ├── RadarPage.tsx
│   │   ├── ProjectCard.tsx        # Expandable — heat strip · signals · slip % · mini-bars
│   │   ├── AiBriefing.tsx         # Collapsible briefing panel with confidence indicator
│   │   └── hooks/usePortfolioData.ts
│   ├── cockpit/                   # PJM Daily Cockpit
│   │   ├── CockpitPage.tsx
│   │   ├── ActionItem.tsx         # Left-border coloured action card
│   │   ├── StandupCard.tsx        # Standup draft + countdown chip + post button
│   │   └── hooks/useTodayActions.ts
│   ├── cr-board/                  # CR Delivery Board
│   │   ├── CrBoardPage.tsx
│   │   ├── StageLanes.tsx         # Horizontal lane counter strip
│   │   ├── CrTable.tsx            # Sortable/filterable table with pagination
│   │   ├── CrDetailDrawer.tsx     # Inline expand — milestone dots · AI risk note · note form
│   │   └── hooks/useCrData.ts
│   ├── bugs/                      # Bug Triage (Prod + UAT tab)
│   │   ├── BugTriagePage.tsx
│   │   ├── ProdBugTable.tsx       # SLA status · reopen chip · BERT suggestion chip
│   │   ├── UatBugTable.tsx        # Cycle count · stage · assignee
│   │   └── hooks/useBugData.ts
│   ├── uat/                       # UAT Tracker (standalone)
│   │   ├── UatTrackerPage.tsx
│   │   ├── UatCycleList.tsx       # Per-CR UAT cycle history
│   │   ├── SignOffPanel.tsx       # Client sign-off status per UAT cycle
│   │   ├── UatEnvHealth.tsx       # UAT environment status (stable/unstable/down)
│   │   └── hooks/useUatData.ts
│   ├── man-days/                  # Man-Day Consumption
│   │   ├── ManDaysPage.tsx
│   │   ├── BurnTable.tsx          # Per-project burn with alert_threshold_pct line
│   │   ├── BurnChart.tsx          # SVG: actual + forecast line + CI bands (80%/95%)
│   │   ├── ForecastPanel.tsx      # "Run forecast" button + proposed actions list
│   │   ├── EditBudgetModal.tsx    # Purchased days · period dates · daily rate — ADMIN or PM with canEditBudget=true
│   │   └── hooks/useManDays.ts
│   ├── alerts/                    # Alert Center
│   │   ├── AlertsPage.tsx
│   │   ├── AlertTable.tsx         # Filterable with pagination
│   │   ├── AlertActionPanel.tsx   # Acknowledge · Assign (owner + follow-up date) · Escalate · Dismiss
│   │   └── hooks/useAlerts.ts
│   ├── reports/                   # Reports
│   │   ├── ReportsPage.tsx
│   │   ├── ReportHistoryTable.tsx # Click-to-expand inline preview with pagination
│   │   ├── GenerateModal.tsx      # Type · client · date range · notes · client-safe toggle
│   │   ├── SchedulesTab.tsx       # PJM view: active schedules list
│   │   └── hooks/useReports.ts
│   ├── capacity/                  # Capacity & Team
│   │   ├── CapacityPage.tsx
│   │   ├── TeamLoadTable.tsx      # Utilisation bars · BERT-suggested assignments
│   │   ├── LeaveCalendar.tsx      # Approved leave blocks with impact button
│   │   ├── AssignmentsTable.tsx   # MD allocation per dev per project
│   │   └── hooks/useCapacity.ts
│   ├── clients/                   # Client Backlog
│   │   ├── ClientBacklogPage.tsx
│   │   ├── ClientHealthCard.tsx   # Per-client score — reads health_green/amber_threshold
│   │   ├── DependencyTracker.tsx  # Blocker list with age badge
│   │   ├── MilestoneCalendar.tsx  # Upcoming milestone list
│   │   └── hooks/useClientData.ts
│   ├── jira-sync/                 # Jira Sync Health (legacy — content moved to integrations/)
│   │   └── JiraSyncPage.tsx       # Component re-exported as JiraIntegration for Integrations tab
│   ├── agent-audit/               # Agent Decision Audit Log
│   │   ├── AgentAuditPage.tsx
│   │   ├── DecisionTable.tsx      # Agent colour-coded · outcome · truncated proposal
│   │   ├── DecisionInspector.tsx  # Full proposal · trigger · outcome_note · tokens
│   │   └── CostSummaryHeader.tsx  # "Total tokens this week · Est. cost"
│   ├── admin/
│   │   ├── AdminConsolePage.tsx               # Top-level tabs: Clients · Projects · Portfolio · Users ·
│   │   │                                      #   Roles · SLA Rules · Lifecycle Mapping · Report Schedules ·
│   │   │                                      #   Notification Rules · Phase Deliveries  (v5.0: last 2 new)
│   │   ├── sla/SlaRulesPage.tsx
│   │   ├── lifecycle/LifecycleMappingPage.tsx
│   │   ├── users/UserManagementPage.tsx
│   │   ├── schedules/ReportSchedulesPage.tsx
│   │   ├── agents/                            # v4.0 Agent Builder; v7.0 merged with Agent Logs
│   │   │   ├── AgentBuilderPage.tsx           # 3 tabs: Agents · Execution logs · Pending approvals
│   │   │   │                                  # (RunStepsPanel, LogStepDetail, AgentFormModal all inline)
│   │   │   ├── AgentRunHistory.tsx            # Inline per-agent run history (used in Agents tab ▾ Runs)
│   │   │   └── AgentBuilderPage.test.tsx      # 64 tests covering all three tabs
│   │   ├── alert-rules/                       # NEW v5.0: Notifications Engine admin
│   │   │   ├── AlertRulesPage.tsx             # 4 tabs: Rules · Escalation Matrix · Global SPOCs · Event Log
│   │   │   └── PhaseDeliveriesPage.tsx        # Per-project phase schedule editor (FSD/DEV/QA/UAT/PROD)
│   │   └── integrations/                      # v5.0: unified — was Slack-only in v4.0
│   │       ├── IntegrationsPage.tsx           # Tabs: Jira · HR System · Slack
│   │       ├── JiraIntegration.tsx            # JiraSyncPage content as a section
│   │       ├── HrmsIntegration.tsx            # generic HR System card — provider dropdown + descriptor-driven settings form; tabs: sync runs · employee mapping · leave data · WFH data · leave balances · attendance · settings
│   │       └── IntegrationsPage.test.tsx      # 28 tests (tab switching + Slack config)
│   └── copilot/                   # Global Copilot Panel
│       ├── GlobalCopilotPanel.tsx # Global floating panel — rendered at Shell level, available to all authenticated users on every page via a floating button
│       ├── CopilotMessage.tsx     # Agent/user bubble
│       ├── ToolCallTrace.tsx      # Monospace tool call + result blocks
│       ├── TypingIndicator.tsx    # Shown on {type:"token"} WS events
│       └── hooks/useCopilotStream.ts
├── api/
│   ├── client.ts                  # Axios instance + JWT interceptors
│   ├── endpoints.ts               # All API URL constants
│   └── types.ts                   # Shared TypeScript types
└── layout/
    ├── Shell.tsx                  # TopBar (top) + routed content (column) + GlobalCopilotPanel
    ├── TopBar.tsx                 # Sticky topbar: brand→/orbit · alerts bell (gated) · avatar→admin dropdown (theme + sign out)
    └── nav.tsx                    # Shared nav model: PRODUCT_NAV (launcher tiles) · ADMIN_NAV (avatar menu) ·
                                   #   ROLE_ACCESS · ROLE_LABEL · OrbitMark · useAllowedScreenIds() · visibleNav()
# features/orbit/OrbitLauncherPage.tsx  → the /orbit hub: hero Orbitter tile + gated PRODUCT_NAV tiles
```

### 2.2 Key Design Decisions (v4.0)

- **Custom design system only.** No Tailwind, no MUI. All tokens in `design/theme.ts` (light + dark palettes; `R` radii; `C.shadow`; `PIE_PAL` chart series). Dark-indigo scheme adopted 2026-07-14 from the approved V3 mock (dark `#5b7cfa` accent is the DEFAULT theme; light is the derived indigo palette; green/amber/red are RAG-only). `ThemeContext` is the single theme state; snapshot/report renders (`?snapshot=1`) are always light.
- **Topbar-only shell** *(2026-07-20)*. The left `Sidebar` (and its mobile hamburger/drawer) is retired in favour of a sticky `TopBar`: `◈ orbit` brand (→ `/orbit`), an alerts bell (gated → `/alerts`), and an avatar dropdown (gated admin/system links + theme toggle + Sign out). Nav to product screens moves to the **`/orbit` launcher** (`OrbitLauncherPage` — hero Orbitter tile + gated `PRODUCT_NAV` tiles). Login/`/`/catch-all still land on `/radar`. The nav model + RBAC×flag gate live in one place (`layout/nav.tsx`: `PRODUCT_NAV`, `ADMIN_NAV`, `useAllowedScreenIds()`, `visibleNav()`), consumed by both TopBar and launcher. `store.collapsed`/`toggleSidebar` are now dead (retained, unused). *(Supersedes the collapsible-sidebar + responsive-hamburger notes below.)*
- **14-day heat strip.** Computed client-side from `risk_score_history[14]` in `ProjectHealthSummary`. Each bar 3px wide × variable height (4–20px). Colour follows risk score: green→amber→orange→red.
- **React Query for all server state.** Dashboard: 2-min TTL. CR/bug tables: 30-sec TTL. Optimistic updates on alert/proposal actions; rollback on server error with toast notification.
- **WebSocket for copilot.** STOMP subscription to `/topic/copilot/{sessionId}`. `{type:"token"}` → append to bubble + show TypingIndicator. `{type:"proposal"}` → render HITL buttons. `{type:"done"}` → hide TypingIndicator.
- **Grounded copilot** *(2026-07-20)*. `POST /copilot/message` accepts an optional `portfolioId` (the panel sends `store.activePortfolioId`). `CopilotController.buildGroundedPrompt` prepends a live delivery digest from `CopilotContextService.buildDigest(portfolioId)` — top-5 open alerts + severity counts (`AlertRepository`), open-CR total + stage rollup (`JiraIssueRepository.findOpenAmCrRows`), prod-bug severity (`countOpenProdBugsByPortfolioAndSeverity`), capacity load (`DeveloperRepository`) — using cheap reads only (no per-project risk scoring). `AiGatewayService` treats placeholder/blank keys as unconfigured, routes by `AI_PROVIDER` (Anthropic|OpenAI), and returns honest data-free notices instead of fabricated text. The prompt instructs the model to answer only from the digest and never invent issue keys/names.
- **Pagination everywhere.** Every table that maps to a `Page<T>` API uses `Pagination.tsx` component. Default page size: 20 for most tables, 10 for audit log and reports.
- **Role-conditioned rendering.** `EditBudgetModal` rendered for `ADMIN` roles and for `PM` users whose JWT `canEditBudget` claim is `true`. Admin section in sidebar only rendered for `ADMIN`. Copilot is a global floating panel (`GlobalCopilotPanel`) rendered at Shell level, accessible to all authenticated users on every page via a floating button. Agent Builder and Integrations pages guard-route to `ADMIN` only.
- **AiSuggestionChip.** Dismissible chip shown on bugs and CRs when BERT classifier has a suggestion. "Accept" writes back via `PATCH /api/v1/issues/{key}/triage`. "Override" opens inline editor.
- **CostSummaryBar.** In Agent Audit Log header: queries `GET /api/v1/agent/cost-summary?period=week` → displays token count + estimated cost (at $0.03/1K tokens for GPT-4o).
- **Agent Builder tool picker.** In `AgentEditModal`, tools are listed from `GET /api/v1/admin/agents/tools`. HITL-required tools display an amber lock icon. Selecting a HITL tool forces the agent's HITL setting to true for that tool even if the agent-level toggle is off.
- **Composite layer over atoms** *(2026-07-08)*. Pages compose `PageHeader`/`StatGrid`/`FilterBar`/`TableWrap`/`StatusPill` instead of hand-rolling layout; page-local status→colour maps are banned (extend `StatusPill`'s `TONE` table). Reference implementation: `features/alerts/AlertsPage.tsx`. Conventions + migration checklist: [`frontend-component-strategy.md`](frontend-component-strategy.md).
- **Responsive Shell** *(2026-07-08)*. `design/useBreakpoint.ts` (`mobile <640 / tablet <1024 / desktop`). Below 640px: sidebar → hamburger top bar + overlay drawer, natural document scroll (no inner-scroll clamp — same layout snapshot mode uses; WebView-friendly for a future mobile wrapper). Desktop and snapshot modes unchanged. Copilot panel desktop-only.
- **Feature-flag gating** *(2026-07-08; nav home moved 2026-07-20)*. `app/featureFlags.tsx` resolves `GET /feature-flags/effective` once (react-query, 60s stale). `layout/nav.tsx#visibleNav` filters launcher tiles + avatar-menu items and `Shell` gates routes on `screen.<navId>`; sections gate via `<Feature flag="section.<page>.<name>">`. Orthogonal to role guards — both apply. See §5.20.

---

## 3. Backend Package Structure

```
com.akki.pjm/
├── AkkiPjmApplication.java
├── config/
│   ├── SecurityConfig.java            # JWT + RBAC + CORS
│   │   # Route guards: @PreAuthorize per controller method
│   ├── RedisConfig.java
│   ├── WebSocketConfig.java           # STOMP broker
│   ├── QuartzConfig.java              # Scheduled jobs
│   └── AiConfig.java                  # LLM + embedding + BERT beans
├── domain/
│   ├── issue/
│   │   ├── JiraIssue.java
│   │   ├── IssueMilestone.java
│   │   ├── IssueTransition.java
│   │   └── IssueNote.java
│   ├── uat/
│   │   ├── UatCycle.java
│   │   ├── UatBug.java
│   │   └── UatSignOff.java
│   ├── client/
│   │   ├── Client.java                # Includes healthGreenThreshold, healthAmberThreshold
│   │   ├── ClientDependency.java
│   │   └── ManDayBudget.java
│   ├── alert/
│   │   ├── Alert.java                 # Includes sourceAgent, followUpDate, phase, daysOverdue (v5.0)
│   │   ├── AlertAction.java
│   │   ├── NotificationRule.java      # NEW v5.0: triggerType, role, phase, offsetDays, triggerTime, enabled
│   │   ├── EscalationConfig.java      # NEW v5.0: role, phase, phaseSpocEmail, deliverySpocEnabled
│   │   ├── GlobalSpocConfig.java      # NEW v5.0: spocType (DELIVERY_SPOC|SOLUTIONS_SPOC|ENG_MANAGER), email, slackUserId
│   │   ├── PhaseStatus.java           # NEW v5.0: project×phase — startDate, endDate, assigneeEmail, status, lastNotifiedT1/T2, ddayNotified
│   │   └── NotificationEvent.java     # NEW v5.0: per-send log — eventType, recipientEmail, sentAt, userResponse
│   ├── agent/
│   │   ├── AgentDecisionLog.java      # Includes outcomeNote, tokensUsed
│   │   ├── AgentProjectSummary.java
│   │   ├── AgentRunLog.java
│   │   ├── AgentDefinition.java       # v4.0+: @JdbcTypeCode(JSON) on triggerConfig + channelConfig; toMap() now returns promptTemplate
│   │   ├── AgentMemory.java           # v4.0: long-term memory entry
│   │   ├── AgentRun.java              # v4.0+: @JdbcTypeCode(JSON) on inputContext; inputContext serialised via Jackson
│   │   └── AgentToolCall.java         # v4.0+: @JdbcTypeCode(JSON) on args + result; result serialised via Jackson
│   ├── config/
│   │   ├── SlackConfig.java           # NEW v4.0: workspace name, bot_token, signing_secret, default_channel
│   │   └── SlackProjectChannel.java   # NEW v4.0: per-project channel mapping (projectId → channelId)
│   ├── report/
│   │   ├── GeneratedReport.java
│   │   ├── ReportTemplate.java
│   │   └── ReportSchedule.java
│   └── capacity/
│       ├── Developer.java
│       ├── Assignment.java
│       └── Leave.java
├── repository/
├── dto/
│   ├── request/
│   └── response/
│       ├── PortfolioRadarResponse.java  # Includes risk_score_history[14]
│       ├── CrDetailResponse.java         # Includes milestones[], aiRiskExplanation
│       ├── ManDayForecastResponse.java   # Includes forecastPoints with CI bands
│       ├── AlertResponse.java            # Includes sourceAgent, followUpDate
│       ├── AgentDecisionResponse.java    # Includes outcomeNote, tokensUsed
│       ├── CostSummaryResponse.java      # tokensThisWeek, estimatedCostUsd
│       ├── AgentDefinitionResponse.java  # NEW v4.0: id, name, type, trigger, tools[], enabled
│       ├── AgentRunResponse.java         # NEW v4.0: run log entry with status, tokensUsed, durationMs
│       ├── SlackConfigResponse.java      # NEW v4.0: workspace, masked token, defaultChannel
│       └── SlackChannelMappingResponse.java # NEW v4.0: projectId, projectName, channelId
├── service/
│   ├── delivery/
│   │   ├── CrService.java
│   │   ├── BugService.java
│   │   ├── UatService.java
│   │   └── MilestoneService.java
│   ├── capacity/
│   │   ├── BandwidthService.java
│   │   └── ManDayConsumptionService.java
│   ├── alert/
│   │   └── AlertEngine.java           # Includes sourceAgent tagging on alert creation
│   ├── report/
│   │   ├── ReportGeneratorService.java
│   │   ├── ReportSchedulerService.java
│   │   └── ExportService.java
│   ├── sync/
│   │   ├── JiraSyncService.java
│   │   └── JiraWebhookProcessor.java
│   ├── ai/
│   │   ├── AiGatewayService.java      # LLM abstraction + cost logging
│   │   ├── EmbeddingService.java
│   │   ├── RiskScoringService.java
│   │   └── BertTriageService.java     # Auto-tag issue type / severity / owner
│   ├── agent/
│   │   ├── DeliveryIntelligenceAgent.java
│   │   ├── ReportDraftingAgent.java
│   │   ├── ManDayForecastAgent.java
│   │   ├── StandupAgent.java
│   │   ├── EscalationAgent.java
│   │   ├── DevReminderAgent.java      # NEW v4.0
│   │   └── tool/                      # NEW v4.0: Tool Registry package
│   │       ├── AgentTool.java         # Interface: toolId(), description(), isHitlRequired(), execute()
│   │       ├── ToolRegistry.java      # Spring bean: discovers all AgentTool implementations
│   │       ├── AgentRuntime.java      # Central execution engine: context → loop → HITL → audit → memory
│   │       ├── HitlGateway.java       # Pauses run, surfaces proposal WS event, waits for approval
│   │       └── AgentMemoryService.java # Redis short-term + DB long-term read/write/search
│   ├── HitlApprovalService.java   # NEW v6.0: approve/reject HITL steps; executes tool via ToolRegistry
│   ├── client/
│   │   └── ClientHealthService.java   # Per-client threshold-aware health scoring
│   └── dashboard/
│       └── DashboardService.java
├── controller/
│   ├── DashboardController.java
│   ├── CrController.java
│   ├── BugController.java
│   ├── UatController.java
│   ├── ManDayController.java
│   ├── AlertController.java
│   ├── ReportController.java
│   ├── ReportScheduleController.java
│   ├── CapacityController.java
│   ├── ClientController.java
│   ├── JiraSyncController.java
│   ├── AgentController.java
│   ├── CopilotController.java
│   ├── AdminController.java           # Extended v4.0: agent CRUD + integrations endpoints
│   ├── AlertRulesController.java      # NEW v5.0: /api/v1/admin/alert-rules/rules|escalation|spocs|events
│   ├── PhaseStatusController.java     # NEW v5.0: /api/v1/admin/phase-statuses (CRUD + status update)
│   ├── AgentLogsController.java       # NEW v6.0: /api/v1/admin/agents/runs + HITL approve/reject
│   └── HrmsSyncController.java        # /api/v1/hrms — providers · status · config · test · runs · sync · leaves · wfh · balances · attendance · employees · webhook
├── service/notification/              # NEW v5.0
│   └── NotificationSchedulerService.java  # @Scheduled every 30 min — T-2/T-1/D-Day/overdue evaluation
├── integration/
│   ├── jira/
│   │   ├── JiraClient.java
│   │   └── JiraWebhookValidator.java
│   ├── forecast/
│   │   └── ProphetForecastClient.java
│   ├── slack/
│   │   └── SlackService.java          # v5.0: sendToChannelDetailed() checks ok-field; resolveChannel() falls back to defaultChannel; getDefaultChannel(); resolveSlackUserId(email)
│   └── notification/
│       ├── SlackNotifier.java         # Thin wrapper → delegates to SlackService
│       └── EmailNotifier.java
└── exception/
    └── GlobalExceptionHandler.java
```

---

## 4. Database Schema (V16–V53 Flyway Migrations)

### clients
```sql
CREATE TABLE clients (
  id                      BIGSERIAL PRIMARY KEY,
  name                    VARCHAR(100) NOT NULL UNIQUE,
  code                    VARCHAR(20)  NOT NULL UNIQUE,
  health_green_threshold  INTEGER DEFAULT 80,   -- per-client; admin-configurable
  health_amber_threshold  INTEGER DEFAULT 60,   -- per-client; admin-configurable
  active                  BOOLEAN DEFAULT TRUE
);
```

### jira_issues
```sql
CREATE TABLE jira_issues (
  id                    BIGSERIAL PRIMARY KEY,
  issue_key             VARCHAR(30)  NOT NULL UNIQUE,
  summary               TEXT,
  issue_type            VARCHAR(20),       -- CR | PROD_BUG | UAT_BUG | TASK
  jira_status           VARCHAR(50),
  lifecycle_stage       VARCHAR(50),       -- Orbit-mapped stage
  priority              VARCHAR(20),
  severity              VARCHAR(10),       -- P0–P3 (bugs only)
  assignee_jira_user    VARCHAR(100),
  project_id            BIGINT REFERENCES projects(id),
  client_id             BIGINT REFERENCES clients(id),
  fix_version           VARCHAR(100),
  labels                TEXT[],
  created_at            TIMESTAMP,         -- Jira fields.created (never sync time)
  updated_at            TIMESTAMP,         -- Jira fields.updated (never sync time)
  resolved_at           TIMESTAMP,         -- Jira fields.resolutiondate (NULL while open)
  last_synced_at        TIMESTAMP,         -- when Orbit last upserted this row
  reopen_count          INTEGER DEFAULT 0,
  dependency_type       VARCHAR(20),       -- CLIENT | INTERNAL | NONE
  hold_reason           TEXT,
  -- AI-generated fields
  bert_suggested_severity VARCHAR(10),     -- P0–P3, nullable — shown as AiSuggestionChip
  bert_suggested_owner    VARCHAR(100),    -- nullable — shown as AiSuggestionChip
  bert_suggestion_accepted BOOLEAN,        -- null=pending, true=accepted, false=overridden
  raw_jira_json           JSONB
);
CREATE INDEX idx_ji_client    ON jira_issues(client_id);
CREATE INDEX idx_ji_assignee  ON jira_issues(assignee_jira_user);
CREATE INDEX idx_ji_stage     ON jira_issues(lifecycle_stage);
CREATE INDEX idx_ji_updated   ON jira_issues(updated_at DESC);
```

### issue_milestones
```sql
CREATE TABLE issue_milestones (
  id               BIGSERIAL PRIMARY KEY,
  issue_id         BIGINT REFERENCES jira_issues(id),
  milestone_type   VARCHAR(20),  -- BRD | FSD | DEV | QA | UAT_SIGNOFF | PROD
  target_date      DATE,
  actual_date      DATE,
  is_tbc           BOOLEAN DEFAULT FALSE,
  status           VARCHAR(20),  -- ON_TRACK | AT_RISK | DELAYED | BREACHED | TBC
  source           VARCHAR(20)   -- JIRA_FIELD | ORBIT_MANUAL | RULE_CALCULATED
);
```

### uat_cycles  *(V24 — promoted to full spec)*
```sql
CREATE TABLE uat_cycles (
  id                BIGSERIAL PRIMARY KEY,
  issue_id          BIGINT REFERENCES jira_issues(id),
  cycle_number      INTEGER NOT NULL,       -- 1, 2, 3 — shown as "Cycle N" in UAT table
  started_at        TIMESTAMP,
  completed_at      TIMESTAMP,
  sign_off_status   VARCHAR(20),  -- PENDING | SIGNED_OFF | REJECTED | WAIVED
  signed_off_by     VARCHAR(100), -- client contact name
  signed_off_at     TIMESTAMP,
  env_snapshot      VARCHAR(50),  -- UAT environment version at cycle start
  notes             TEXT
);
CREATE INDEX idx_uat_issue ON uat_cycles(issue_id);
```

### client_dependencies  *(V37)*
```sql
CREATE TABLE client_dependencies (
  id              BIGSERIAL PRIMARY KEY,
  client_id       BIGINT REFERENCES clients(id),
  title           VARCHAR(200) NOT NULL,
  description     TEXT,
  dep_type        VARCHAR(20),  -- CLIENT | INTERNAL | EXTERNAL
  issue_id        BIGINT REFERENCES jira_issues(id),  -- optional link
  raised_at       DATE,
  resolved_at     DATE,
  status          VARCHAR(20) DEFAULT 'OPEN'  -- OPEN | RESOLVED | WAIVED
);
```

### sla_rules
```sql
CREATE TABLE sla_rules (
  id               BIGSERIAL PRIMARY KEY,
  client_id        BIGINT REFERENCES clients(id),  -- NULL = global
  severity         VARCHAR(10),
  response_hours   DECIMAL(5,1),
  resolution_hours DECIMAL(5,1),
  include_weekends BOOLEAN DEFAULT FALSE
);
```

### alerts  *(updated v5.0 — phase and daysOverdue columns added)*
```sql
CREATE TABLE alerts (
  id               BIGSERIAL PRIMARY KEY,
  alert_type       VARCHAR(50),
  severity         VARCHAR(10),   -- critical | risk | info
  issue_id         BIGINT REFERENCES jira_issues(id),
  client_id        BIGINT REFERENCES clients(id),
  project_id       BIGINT REFERENCES projects(id),
  title            VARCHAR(200),
  detail           TEXT,
  source_agent     VARCHAR(50),   -- which agent or service raised this alert
  status           VARCHAR(20) DEFAULT 'OPEN',  -- OPEN | ACKNOWLEDGED | DISMISSED | MITIGATED
  owner_name       VARCHAR(100),
  follow_up_date   DATE,
  mitigation_note  TEXT,
  ai_explanation   TEXT,
  phase            VARCHAR(20),   -- NEW v5.0: FSD | DEV | QA | UAT | PROD (for PHASE_OVERDUE alerts)
  days_overdue     INT,           -- NEW v5.0: populated by NotificationSchedulerService
  created_at       TIMESTAMP DEFAULT NOW(),
  resolved_at      TIMESTAMP
);
CREATE INDEX idx_al_phase ON alerts(phase);  -- NEW v5.0
```

### app_users — v9.0 column addition
```sql
-- NEW v9.0
ALTER TABLE app_users ADD COLUMN can_edit_budget BOOLEAN DEFAULT false;
-- Fine-grained permission: PM users with this flag can edit man-day budgets
-- Exposed as canEditBudget claim in JWT via JwtService.generate()
```

### man_day_budgets
```sql
CREATE TABLE man_day_budgets (
  id                  BIGSERIAL PRIMARY KEY,
  project_id          BIGINT REFERENCES projects(id) UNIQUE,
  purchased_days      DECIMAL(10,2),
  period_start        DATE,
  period_end          DATE,
  daily_rate_hours    DECIMAL(5,2) DEFAULT 8.0,
  alert_threshold_pct INTEGER DEFAULT 80    -- vertical threshold line on burn chart
);
```

### man_day_snapshots
```sql
CREATE TABLE man_day_snapshots (
  id                      BIGSERIAL PRIMARY KEY,
  project_id              BIGINT REFERENCES projects(id),
  snapshot_date           DATE,
  burned_days             DECIMAL(10,2),
  remaining_days          DECIMAL(10,2),
  burn_rate_per_day       DECIMAL(6,3),
  forecast_exhaustion     DATE,
  forecast_confidence     VARCHAR(10)  -- HIGH | MEDIUM | LOW
);
CREATE INDEX idx_mds_project_date ON man_day_snapshots(project_id, snapshot_date DESC);
```

### report_schedules  *(V30)*
```sql
CREATE TABLE report_schedules (
  id              BIGSERIAL PRIMARY KEY,
  report_type     VARCHAR(50),        -- WEEKLY_DELIVERY | DAILY_SNAPSHOT | EXEC_SUMMARY | etc.
  client_id       BIGINT REFERENCES clients(id),  -- NULL = all clients
  cron_expression VARCHAR(50),        -- e.g. "0 0 8 * * MON" (every Monday 8am)
  recipients      TEXT[],             -- email addresses
  include_client_safe_filter BOOLEAN DEFAULT TRUE,
  created_by      BIGINT REFERENCES app_users(id),
  active          BOOLEAN DEFAULT TRUE,
  last_run_at     TIMESTAMP,
  next_run_at     TIMESTAMP
);
```

### Agent memory tables *(V34–V35 + V49–V52 new)*
```sql
-- Existing: rolling project summary for agent context
CREATE TABLE agent_project_summaries (
  id            BIGSERIAL PRIMARY KEY,
  project_id    BIGINT REFERENCES projects(id) UNIQUE,
  summary_text  TEXT,
  updated_at    TIMESTAMP DEFAULT NOW(),
  token_count   INTEGER
);

-- Existing: HITL decision audit log
CREATE TABLE agent_decision_log (
  id            BIGSERIAL PRIMARY KEY,
  agent_name    VARCHAR(50),
  trigger_event TEXT,
  proposal_json JSONB,
  outcome       VARCHAR(20),   -- APPROVED | EDITED | REJECTED
  outcome_note  TEXT,          -- reason text, required for REJECTED
  tokens_used   INTEGER,       -- per-call token count for cost tracking
  decided_by    BIGINT REFERENCES app_users(id),
  decided_at    TIMESTAMP
);

-- Existing: issue embeddings for semantic search
CREATE TABLE issue_embeddings (
  id            BIGSERIAL PRIMARY KEY,
  issue_id      BIGINT REFERENCES jira_issues(id) UNIQUE,
  embedding     vector(1536),
  embedded_at   TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_ie_embedding ON issue_embeddings
  USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- NEW V49: Slack workspace configuration
CREATE TABLE slack_config (
  id              BIGSERIAL PRIMARY KEY,
  workspace_name  VARCHAR(100),
  bot_token       TEXT NOT NULL,           -- encrypted at rest; starts with xoxb-
  signing_secret  TEXT NOT NULL,           -- encrypted at rest; used to validate incoming events
  default_channel VARCHAR(100),            -- fallback channel ID when no project mapping found
  created_at      TIMESTAMP DEFAULT NOW(),
  updated_at      TIMESTAMP DEFAULT NOW()
);

-- NEW V49: Per-project Slack channel mapping
CREATE TABLE slack_project_channels (
  id          BIGSERIAL PRIMARY KEY,
  project_id  BIGINT REFERENCES projects(id) UNIQUE,
  channel_id  VARCHAR(50) NOT NULL,        -- Slack channel ID e.g. C04ABCDE123
  channel_name VARCHAR(100),              -- display name, cached from Slack API
  updated_at  TIMESTAMP DEFAULT NOW()
);

-- NEW V50: Configurable agent definitions (Agent Builder)
CREATE TABLE agent_definitions (
  id              BIGSERIAL PRIMARY KEY,
  name            VARCHAR(100) NOT NULL UNIQUE,
  agent_type      VARCHAR(30) NOT NULL,    -- INTELLIGENCE | REMINDER | REPORTING | ESCALATION | FORECAST | STANDUP | CUSTOM
  trigger_type    VARCHAR(20) NOT NULL,    -- CRON | WEBHOOK | THRESHOLD | MANUAL
  trigger_config  JSONB,                   -- e.g. {"cron":"0 0 9 * * MON-FRI"} or {"threshold":"overdue_count > 5"}
  allowed_tools   TEXT[],                  -- tool IDs from ToolRegistry this agent may call
  system_prompt   TEXT,                    -- LLM system prompt
  output_channel  VARCHAR(50),             -- "slack" | "email" | "copilot" | null
  slack_channel_id VARCHAR(50),            -- if output_channel = "slack"
  hitl_required   BOOLEAN DEFAULT TRUE,
  enabled         BOOLEAN DEFAULT TRUE,
  is_builtin      BOOLEAN DEFAULT FALSE,   -- built-in agents: disable allowed, delete forbidden
  created_by      BIGINT REFERENCES app_users(id),
  created_at      TIMESTAMP DEFAULT NOW(),
  updated_at      TIMESTAMP DEFAULT NOW()
);

-- NEW V51: Agent long-term memory (cross-run, per agent+project)
CREATE TABLE agent_memory (
  id            BIGSERIAL PRIMARY KEY,
  agent_id      BIGINT REFERENCES agent_definitions(id),
  project_id    BIGINT REFERENCES projects(id),
  mem_key       VARCHAR(100) NOT NULL,     -- e.g. "last_reminder_items", "pattern_notes"
  mem_value     TEXT NOT NULL,
  memory_type   VARCHAR(20) DEFAULT 'FACT', -- FACT | PATTERN | DECISION | REMINDER
  embedding     vector(1536),              -- for memory.search semantic lookup
  expires_at    TIMESTAMP,                 -- NULL = permanent
  created_at    TIMESTAMP DEFAULT NOW(),
  updated_at    TIMESTAMP DEFAULT NOW(),
  UNIQUE (agent_id, project_id, mem_key)
);
CREATE INDEX idx_am_agent_project ON agent_memory(agent_id, project_id);
CREATE INDEX idx_am_embedding ON agent_memory
  USING ivfflat (embedding vector_cosine_ops) WITH (lists = 50);

-- NEW V52: Agent run execution log
CREATE TABLE agent_runs (
  id              BIGSERIAL PRIMARY KEY,
  agent_id        BIGINT REFERENCES agent_definitions(id),
  project_id      BIGINT REFERENCES projects(id),
  triggered_by    VARCHAR(50),             -- "CRON" | "WEBHOOK" | "MANUAL" | "THRESHOLD"
  trigger_context JSONB,                   -- event payload or threshold data that caused the run
  status          VARCHAR(20) DEFAULT 'RUNNING', -- RUNNING | COMPLETED | FAILED | HITL_PENDING
  input_context   TEXT,                    -- compressed context fed to LLM at run start (~500 tokens)
  output_summary  TEXT,                    -- run outcome summary
  tokens_used     INTEGER,
  duration_ms     INTEGER,
  started_at      TIMESTAMP DEFAULT NOW(),
  completed_at    TIMESTAMP
);
CREATE INDEX idx_ar_agent ON agent_runs(agent_id, started_at DESC);

-- NEW V53: Per-run tool call audit
CREATE TABLE agent_tool_calls (
  id            BIGSERIAL PRIMARY KEY,
  run_id        BIGINT REFERENCES agent_runs(id),
  tool_name     VARCHAR(100) NOT NULL,
  args          JSONB,
  result        JSONB,
  hitl_required BOOLEAN DEFAULT FALSE,
  hitl_outcome  VARCHAR(20),              -- APPROVED | REJECTED | EDITED | null (if no HITL)
  hitl_note     TEXT,                     -- outcome_note when REJECTED
  called_at     TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_atc_run ON agent_tool_calls(run_id);
```

### Notifications Engine tables *(V54–V58 — new v5.0)*
```sql
-- V54: Notification rules (23 seeded — T-2, T-1, D-Day, digest per role/phase)
CREATE TABLE notification_rules (
  id                     BIGSERIAL    PRIMARY KEY,
  rule_name              VARCHAR(150) NOT NULL,
  trigger_type           VARCHAR(20)  NOT NULL,   -- PRE_DUE | DDAY | OVERDUE | DIGEST
  role                   VARCHAR(50),              -- DEVELOPER | TECH_LEAD | QA_LEAD | PROJECT_MANAGER | SOLUTION_MANAGER | ENG_MANAGER | DELIVERY_SPOC | SOLUTIONS_SPOC
  phase                  VARCHAR(20),              -- FSD | DEV | QA | UAT | PROD
  offset_days            INT          DEFAULT 0,   -- days before end_date (2=T-2, 1=T-1, 0=D-Day)
  trigger_time           VARCHAR(5)   DEFAULT '09:00',
  enabled                BOOLEAN      DEFAULT true,
  overdue_interval_hours INT          DEFAULT 3,
  overdue_window_start   VARCHAR(5)   DEFAULT '09:00',
  overdue_window_end     VARCHAR(5)   DEFAULT '21:00',
  template_id            VARCHAR(50),
  created_at             TIMESTAMP    DEFAULT NOW()
);

-- V55: Per role/phase escalation config + global SPOCs
CREATE TABLE escalation_config (
  id                    BIGSERIAL    PRIMARY KEY,
  role                  VARCHAR(50)  NOT NULL,
  phase                 VARCHAR(20),
  phase_spoc_email      VARCHAR(255),
  phase_spoc_name       VARCHAR(255),
  delivery_spoc_enabled BOOLEAN      DEFAULT true,
  re_escalation_hours   INT          DEFAULT 24,
  updated_at            TIMESTAMP    DEFAULT NOW()
);

CREATE TABLE global_spoc_config (
  id            BIGSERIAL   PRIMARY KEY,
  spoc_type     VARCHAR(30) NOT NULL UNIQUE,  -- DELIVERY_SPOC | SOLUTIONS_SPOC | ENG_MANAGER
  email         VARCHAR(255),
  name          VARCHAR(255),
  slack_user_id VARCHAR(50),
  updated_at    TIMESTAMP   DEFAULT NOW()
);

-- V56: Phase delivery schedule per project
CREATE TABLE phase_statuses (
  id                 BIGSERIAL    PRIMARY KEY,
  project_id         BIGINT       REFERENCES projects(id) ON DELETE CASCADE,
  phase              VARCHAR(20)  NOT NULL,   -- FSD | DEV | QA | UAT | PROD
  start_date         DATE,
  end_date           DATE,
  assignee_email     VARCHAR(255),
  assignee_name      VARCHAR(255),
  status             VARCHAR(30)  DEFAULT 'NOT_STARTED',
  -- NOT_STARTED | IN_PROGRESS | ON_TRACK | DELAYED_SELF | DELAYED_SYSTEM | COMPLETED
  delay_note         TEXT,
  jira_issue_key     VARCHAR(50),
  last_notified_t2   DATE,         -- dedup: don't re-send T-2 on same day
  last_notified_t1   DATE,         -- dedup: don't re-send T-1 on same day
  dday_notified      BOOLEAN      DEFAULT false,
  updated_at         TIMESTAMP    DEFAULT NOW(),
  UNIQUE (project_id, phase)
);
CREATE INDEX idx_ps_end_date ON phase_statuses(end_date);
CREATE INDEX idx_ps_status   ON phase_statuses(status);

-- V57: Notification event log (every DM/channel send)
CREATE TABLE notification_events (
  id              BIGSERIAL   PRIMARY KEY,
  rule_id         BIGINT      REFERENCES notification_rules(id),
  project_id      BIGINT      REFERENCES projects(id),
  phase_status_id BIGINT      REFERENCES phase_statuses(id),
  phase           VARCHAR(20),
  event_type      VARCHAR(30),   -- T2_REMINDER | T1_REMINDER | DDAY_PROMPT | ESCALATION | OVERDUE_LOOP | DIGEST
  recipient_email VARCHAR(255),
  recipient_name  VARCHAR(255),
  slack_msg_ts    VARCHAR(50),   -- Slack message timestamp for threading
  user_response   VARCHAR(30),   -- ON_TRACK | DELAYED | COMPLETED | IN_PROGRESS (Phase 3)
  responded_at    TIMESTAMP,
  sent_at         TIMESTAMP   DEFAULT NOW()
);
CREATE INDEX idx_ne_event_type ON notification_events(event_type);
CREATE INDEX idx_ne_sent_at    ON notification_events(sent_at);

-- V58: Add phase tracking columns to alerts
ALTER TABLE alerts ADD COLUMN phase        VARCHAR(20);
ALTER TABLE alerts ADD COLUMN days_overdue INT;
CREATE INDEX idx_al_phase ON alerts(phase);

-- V59: Update role_screen_config to replace jira/darwin with integrations
-- V62 (patch): role renames PM/ENGINEERING, remove cockpit, add Orbitter home (radar)
UPDATE role_screen_config SET screen_ids = 'radar,cr,bugs,uat,mandays,alerts,reports,capacity,clients,integrations,audit,admin,agent-builder' WHERE role_name = 'ADMIN';
UPDATE role_screen_config SET screen_ids = 'radar,cr,bugs,uat,mandays,alerts,reports,capacity,clients,integrations,audit,agent-builder' WHERE role_name = 'PM';
UPDATE role_screen_config SET screen_ids = 'radar' WHERE role_name = 'LEADERSHIP';
UPDATE role_screen_config SET screen_ids = 'radar,capacity,mandays,agent-builder' WHERE role_name = 'ENGINEERING';
UPDATE role_screen_config SET screen_ids = 'radar,clients,alerts,reports' WHERE role_name = 'CSM';
UPDATE role_screen_config SET screen_ids = 'radar,mandays,reports,capacity' WHERE role_name = 'REVENUE';
```

---

## 5. Core API Contracts (v5.0)

### 5.1 Dashboard

```
GET  /api/v1/dashboard/radar
     Auth: LEADERSHIP | ADMIN
     → PortfolioRadarResponse {
         projects: ProjectHealthSummary[] {
           id, name, client, riskLevel, slipProbabilityPct,
           burnPct, loadPct, msPct,
           risk_score_history: int[14],     // heat strip data
           forecastExhaustionDate,           // from ManDayConsumptionService
           signals: string[],
           aiInsight: string
         },
         alertSummary: { critical, high, medium, low },
         teamCapacity: { overloaded, busy, available, onLeave },
         syncHealth: { lastSyncedAt, status },
         aiBriefing: {
           generatedAt, confidence,          // HIGH | MEDIUM | LOW
           bullets: [{ level, text }]
         }
       }
     Cache: Redis 120s; invalidated on new CRITICAL alert or Jira sync completion.

GET  /api/v1/dashboard/cockpit?userId={id}
     Auth: PM | ADMIN
     → CockpitResponse {
         greeting: string,
         actions: ActionItem[] { id, severity, tag, title, body, buttons[] },
         standupDraft: {
           lines: string[],
           autoPostAt: ISO8601,             // 30min from generation
           secondsUntilAutoPost: int
         },
         pendingProposals: AgentProposal[]
       }
```

### 5.2 CR Board

```
GET  /api/v1/cr?clientId=&portfolioId=&stage=&search=&sort=&page=&size=
     Auth: PM | ADMIN | → Page<CrResponse>

GET  /api/v1/cr/stage-summary?clientId=&portfolioId=
     Auth: PM | ADMIN | → Map<CrStage, Integer>

GET  /api/v1/cr/{issueKey}
     Auth: PM | ADMIN
     → CrDetailResponse {
         issue: CrResponse,
         milestones: IssueMilestone[] {
           milestoneType, targetDate, actualDate, isTbc, status
         },
         notes: IssueNote[],
         aiRiskExplanation: string,     // LLM-generated
         capacityRisk: string,          // e.g. "Arjun at 96% — 4 CRs"
         bertSuggestion: {              // null if no suggestion
           severity, suggestedOwner, accepted
         }
       }

POST /api/v1/cr/{issueKey}/notes
     Auth: PM | ADMIN | Body: { text, isClientSafe: boolean }
     → NoteResponse

PATCH /api/v1/issues/{issueKey}/triage
     Auth: PM | ADMIN
     Body: { acceptBertSeverity: boolean, acceptBertOwner: boolean, overrideSeverity?, overrideOwner? }
     → IssueResponse
```

### 5.3 Bug Triage

```
GET  /api/v1/bugs/prod?severity=&slaStatus=&clientId=&page=&size=
     Auth: PM | ADMIN | → Page<BugResponse> {
       issueKey, summary, severity, slaStatus, slaRemainingHours,
       client, assignee, age, reopenCount, bertSuggestion
     }

GET  /api/v1/bugs/prod/summary?clientId=
     Auth: PM | ADMIN | → ProdBugSummary { p0Open, p1Open, slaAtRisk, slaBreached, reopened, unassigned }

GET  /api/v1/bugs/uat?clientId=&stage=&page=&size=
     Auth: PM | ADMIN | → Page<UatBugResponse> {
       issueKey, summary, severity, stage, assignee, cycleNumber, age, client
     }
```

### 5.4 UAT Tracker

```
GET  /api/v1/uat/cycles?clientId=&crKey=&page=&size=
     Auth: PM | ADMIN
     → Page<UatCycleResponse> {
         cycleId, issueKey, crSummary, cycleNumber,
         startedAt, completedAt, signOffStatus, signedOffBy,
         envSnapshot, bugCount, criticalBugCount
       }

GET  /api/v1/uat/sign-off-status?clientId=
     Auth: PM | ADMIN
     → UatSignOffSummary { totalCRsInUAT, signedOff, rejected, pending }

POST /api/v1/uat/cycles/{cycleId}/sign-off
     Auth: PM | ADMIN
     Body: { status: SIGNED_OFF|REJECTED|WAIVED, signedOffBy, notes }
     → UatCycleResponse
```

### 5.5 Man-Days

```
GET  /api/v1/man-days?projectId=
     Auth: PM | ENGINEERING | ADMIN
     → ManDayConsumptionResponse {
         purchasedDays, burnedDays, remainingDays,
         burnPct, burnRatePerDay, forecastExhaustionDate,
         forecastConfidence, status, trend,
         alertThresholdPct,                // from man_day_budgets.alert_threshold_pct
         dailyHistory: DailyBurnPoint[]
       }

GET  /api/v1/man-days/forecast?projectId=
     Auth: PM | ENGINEERING | ADMIN
     → ManDayForecastResponse {
         forecast: ForecastPoint[] {
           ds: date, yhat: decimal,
           yhatLower80, yhatUpper80,        // 80% CI band
           yhatLower95, yhatUpper95         // 95% CI band
         },
         interpretation: string,           // LLM-generated narrative
         proposedActions: string[]         // e.g. ["Reduce Arjun's allocation 20%"]
       }

PUT  /api/v1/man-days/budget?projectId=
     Auth: PM | ADMIN (non-Admin PM must have canEditBudget=true in JWT)   (@PreAuthorize enforced)
     Body: { purchasedDays, periodStart, periodEnd, dailyRateHours, alertThresholdPct }
     → ManDayBudgetResponse

GET  /api/v1/man-days/portfolio-summary?portfolioId=
     Auth: PM | ENGINEERING | REVENUE | ADMIN
     → {
         portfolioId, soldMandays, consumedMandays, remainingMandays,
         burnPct, projectCount
       }
```

### 5.6 Alerts

```
GET  /api/v1/alerts?severity=&type=&clientId=&status=&page=&size=
     Auth: PM | ADMIN | → Page<AlertResponse> { ..., sourceAgent, followUpDate }

POST /api/v1/alerts/{id}/acknowledge
     Auth: PM | ADMIN | → AlertResponse

POST /api/v1/alerts/{id}/assign
     Auth: PM | ADMIN
     Body: { ownerId, followUpDate: ISO8601 }   // followUpDate required
     → AlertResponse

POST /api/v1/alerts/{id}/dismiss
     Auth: PM | ADMIN | Body: { reason } | → AlertResponse
```

### 5.7 Reports

```
POST /api/v1/reports/generate
     Auth: PM | ADMIN
     Body: { type, clientId, projectId, templateId, manualNotes, clientSafeFilter: boolean }
     → { id, status: GENERATING }
     # Completion fires WS event to /topic/reports/{userId}: { type:"report_ready", reportId }

GET  /api/v1/reports?clientId=&type=&page=&size=
     Auth: PM | ADMIN | → Page<GeneratedReportResponse>

GET  /api/v1/reports/{id}/preview
     Auth: PM | ADMIN | → ReportPreviewResponse { sections: [{ title, content }] }

GET  /api/v1/reports/{id}/download?format=WORD|PDF|EXCEL
     Auth: PM | ADMIN | → File stream
```

### 5.8 Report Schedules

```
GET  /api/v1/report-schedules?clientId=&page=&size=
     Auth: PM (own schedules) | ADMIN (all)
     → Page<ReportScheduleResponse>

POST /api/v1/report-schedules
     Auth: PM | ADMIN | Body: { reportType, clientId, cronExpression, recipients[], clientSafeFilter }
     → ReportScheduleResponse

PUT  /api/v1/report-schedules/{id}
     Auth: ADMIN | → ReportScheduleResponse

DELETE /api/v1/report-schedules/{id}
     Auth: ADMIN | → 204
```

### 5.9 Client Backlog

```
GET  /api/v1/clients
     Auth: PM | ADMIN | → ClientHealthSummary[] {
       id, name, code, healthScore,
       healthGreenThreshold, healthAmberThreshold,    // per-client
       openCrs, p0p1Bugs, tbcDates, burnPct, contact
     }

GET  /api/v1/clients/{id}/dependencies
     Auth: PM | ADMIN | → ClientDependency[] { id, title, description, depType, age, status }

POST /api/v1/clients/{id}/dependencies
     Auth: PM | ADMIN | Body: { title, description, depType, issueKey? }
     → ClientDependency

PATCH /api/v1/clients/{id}/thresholds
     Auth: ADMIN
     Body: { healthGreenThreshold, healthAmberThreshold }
     → ClientResponse
```

### 5.10 Agent / Copilot

```
POST /api/v1/copilot/message
     Auth: All roles (Leadership: read-only Q&A mode)
     Body: { sessionId, text, context: { projectId?, issueKey? } }
     → 202 Accepted
     # Streams via WS /topic/copilot/{sessionId}:
     #   {type:"token", content:"..."}         → append to bubble, show TypingIndicator
     #   {type:"tool_call", name, args}         → show ToolCallTrace block
     #   {type:"tool_result", name, summary}    → show ToolCallTrace result
     #   {type:"proposal", id, action, payload} → render HITL buttons
     #   {type:"done"}                          → hide TypingIndicator

POST /api/v1/agent/proposals/{id}/approve
POST /api/v1/agent/proposals/{id}/reject   Body: { reason }   // reason stored as outcome_note
POST /api/v1/agent/proposals/{id}/edit     Body: { editedPayload }

GET  /api/v1/agent/decisions?projectId=&agentName=&outcome=&page=&size=
     Auth: ADMIN | → Page<AgentDecisionLogEntry> {
       ..., outcomeNote, tokensUsed
     }

GET  /api/v1/agent/cost-summary?period=week|month
     Auth: ADMIN
     → CostSummaryResponse { tokensTotal, estimatedCostUsd, byAgent: Map<String,Integer> }
```

### 5.11 Agent Management API *(new v4.0)*

```
GET  /api/v1/admin/agents?page=&size=
     Auth: ADMIN
     → Page<AgentDefinitionResponse> {
         id, name, agentType, triggerType, triggerConfig,
         allowedTools: string[], systemPrompt, outputChannel,
         slackChannelId, hitlRequired, enabled, isBuiltin,
         lastRunAt, lastRunStatus
       }

POST /api/v1/admin/agents
     Auth: ADMIN
     Body: {
       name, agentType, triggerType, triggerConfig,
       allowedTools: string[], systemPrompt,
       outputChannel, slackChannelId, hitlRequired
     }
     → AgentDefinitionResponse

PUT  /api/v1/admin/agents/{id}
     Auth: ADMIN
     Body: (same fields as POST; isBuiltin agents: name and agentType are immutable)
     → AgentDefinitionResponse

PATCH /api/v1/admin/agents/{id}/toggle
     Auth: ADMIN
     Body: { enabled: boolean }
     → AgentDefinitionResponse

DELETE /api/v1/admin/agents/{id}
     Auth: ADMIN
     Constraint: 404 if not found; 409 if isBuiltin=true; 409 if agent has run history
     → 204

POST /api/v1/admin/agents/{id}/test-run
     Auth: ADMIN
     Body: { projectId? }
     Notes: Executes non-HITL tools for real; HITL tools queued as AWAITING_HITL.
            Run stored in agent_runs with triggeredBy="MANUAL_TEST". inputContext={"dryRun":true}.
     → {
         runId: int,
         status: string,       // COMPLETED | FAILED
         durationMs: int,
         errorMessage: string?,
         steps: [{             // one entry per tool call in agent_tool_calls
           tool: string,
           status: string,     // EXECUTED | AWAITING_HITL | ERROR
           hitlRequired: boolean,
           result: string,     // JSON string of tool result
           error: string?
         }]
       }

GET  /api/v1/admin/agents/{id}/runs?page=&size=
     Auth: ADMIN
     → Page<AgentRunResponse> {
         id, agentId, triggeredBy, status, outputSummary, tokensUsed, durationMs, startedAt, completedAt
       }

GET  /api/v1/admin/agents/{id}/runs/{runId}/steps
     Auth: ADMIN
     Notes: Returns tool-call detail for any historical run.
     → [{
         tool, status, hitlRequired, result, calledAt
       }]

GET  /api/v1/admin/agents/tools
     Auth: ADMIN
     → [{ id, description, requiresHitl }]
     # Lists all ToolRegistry-registered tools — used by Agent Builder tool picker
```

### 5.13 Notification Rules API *(new v5.0)*

```
GET  /api/v1/admin/alert-rules/rules
     Auth: ADMIN
     → NotificationRule[] { id, ruleName, triggerType, role, phase, offsetDays, triggerTime, enabled,
                             overdueIntervalHours, overdueWindowStart, overdueWindowEnd }

PUT  /api/v1/admin/alert-rules/rules/{id}/toggle
     Auth: ADMIN
     → NotificationRule (with enabled flipped)

PUT  /api/v1/admin/alert-rules/rules/{id}
     Auth: ADMIN
     Body: { triggerTime?, overdueIntervalHours?, overdueWindowStart?, overdueWindowEnd? }
     → NotificationRule

GET  /api/v1/admin/alert-rules/escalation
     Auth: ADMIN
     → EscalationConfig[] { id, role, phase, phaseSpocEmail, phaseSpocName, deliverySpocEnabled, reEscalationHours }

PUT  /api/v1/admin/alert-rules/escalation/{id}
     Auth: ADMIN
     Body: { phaseSpocEmail?, phaseSpocName?, deliverySpocEnabled?, reEscalationHours? }
     → EscalationConfig

GET  /api/v1/admin/alert-rules/spocs
     Auth: ADMIN
     → GlobalSpocConfig[] { id, spocType, email, name, slackUserId }

PUT  /api/v1/admin/alert-rules/spocs/{id}
     Auth: ADMIN
     Body: { email?, name?, slackUserId? }
     → GlobalSpocConfig

GET  /api/v1/admin/alert-rules/events?page=&size=
     Auth: ADMIN
     → Page<NotificationEvent> { id, phase, eventType, recipientEmail, recipientName,
                                   userResponse, sentAt, respondedAt, project }
```

### 5.14 Phase Status API *(new v5.0)*

```
GET  /api/v1/admin/phase-statuses
     Auth: PM | ADMIN
     → PhaseStatus[] (all projects, all phases)

GET  /api/v1/admin/phase-statuses/project/{projectId}
     Auth: PM | ADMIN
     → PhaseStatus[] { id, projectId, projectName, phase, startDate, endDate,
                        assigneeEmail, assigneeName, status, delayNote, jiraIssueKey,
                        ddayNotified, updatedAt }

POST /api/v1/admin/phase-statuses
     Auth: PM | ADMIN
     Body: { projectId, phase, startDate?, endDate?, assigneeEmail?, assigneeName?, jiraIssueKey? }
     → PhaseStatus

PUT  /api/v1/admin/phase-statuses/{id}
     Auth: PM | ADMIN
     Body: { startDate?, endDate?, assigneeEmail?, assigneeName?, jiraIssueKey?, status?, delayNote? }
     → PhaseStatus

POST /api/v1/admin/phase-statuses/{id}/status
     Auth: PM | ADMIN
     Body: { status, delayNote? }
     → PhaseStatus

DELETE /api/v1/admin/phase-statuses/{id}
     Auth: ADMIN
     → 204
```

### 5.15 Agent Logs + HITL Approvals API *(new v6.0)*

```
GET  /api/v1/admin/agents/runs?agentId=&status=&page=&size=
     Auth: ADMIN
     → Page<AgentRunSummary> {
         id, agentId, agentName, agentType, triggeredBy, status,
         outputSummary, errorMessage, durationMs, startedAt, completedAt,
         pendingHitl: int   // count of AWAITING_HITL steps in this run
       }

GET  /api/v1/admin/agents/runs/pending-hitl
     Auth: ADMIN
     → [{
         id (stepId), runId, tool, status:"AWAITING_HITL",
         hitlRequired, args, calledAt,
         agentId, agentName, agentType, triggeredBy, runStartedAt
       }]
     Refreshed on a 30-second poll from the frontend.

GET  /api/v1/admin/agents/runs/{runId}/steps
     Auth: ADMIN
     → [{
         id, runId, tool, status, hitlRequired, hitlOutcome,
         args, result, hitlNote, calledAt
       }]
     status values: EXECUTED | APPROVED | APPROVED_WITH_ERROR | AWAITING_HITL | REJECTED | ERROR

POST /api/v1/admin/agents/runs/{runId}/steps/{stepId}/approve
     Auth: ADMIN
     Body (optional): { editedArgs: {} }   // pass to override original tool args
     Notes: Finds the AWAITING_HITL step, resolves final args (editedArgs if present,
            else stored args), executes the tool via ToolRegistry, updates step
            hitlOutcome=APPROVED (or APPROVED_WITH_ERROR on tool failure),
            writes agent_decision_log entry.
     → { ok: true, result: {} }
     Errors: 400 if step not in AWAITING_HITL state · 404 if run/step not found

POST /api/v1/admin/agents/runs/{runId}/steps/{stepId}/reject
     Auth: ADMIN
     Body: { reason: string }   // required — stored as hitlNote + agent_decision_log.outcome_note
     Notes: Sets step hitlOutcome=REJECTED, result={status:"rejected"}, writes decision log.
     → { ok: true }
     Errors: 400 if reason blank · 400 if step not in AWAITING_HITL state · 404 if not found
```

### 5.16 HRMS API *(generalized from the v8.0 Darwinbox API — V91)*

HR integration is a pluggable connector factory (`connector/hrms/`): `HrmsConnector`
beans (providerKey, displayName, settings descriptor, testConnection, sync, webhook
processing) are collected by `HrmsConnectorFactory`; `HrmsSyncService` resolves the
single `hrms_config` row (provider_key + JSONB settings + enabled) and owns run
bookkeeping + the scheduled delta sync (`orbit.hrms.sync-cron`). No provider
configured → sync is a no-op and capacity views degrade gracefully. Darwinbox is the
reference provider (`connector/hrms/darwinbox/DarwinboxHrmsConnector`); adding
Keka/BambooHR/Workday is one class + one bean.

```
GET  /api/v1/hrms/providers
     Auth: PM|ADMIN
     → [{ key, name, fields: [{ key, label, type, required, secret, placeholder, options }] }]
     Registered connectors + their settings descriptors — drives the FE provider
     dropdown and the dynamic settings form. type: text|url|password|select|number.

GET  /api/v1/hrms/status
     Auth: PM|ADMIN
     → { provider, providerName, enabled, configured, lastSyncStatus, lastSyncAt }

GET  /api/v1/hrms/config
     Auth: PM|ADMIN
     → { provider, providerName, enabled, settings{non-secret k:v}, secretsSet{key:bool} }
     Secrets are never echoed — only a set/unset flag per secret field.

PUT  /api/v1/hrms/config
     Auth: ADMIN
     Body: { provider, enabled, settings{...} }
     Blank/masked secret values keep the stored secret; switching provider discards
     old settings; `url`-type fields pass SafeUrl.validatePublicHttps (anti-SSRF).

POST /api/v1/hrms/test
     Auth: PM|ADMIN
     → { ok, message|error }   # cheap live-credential probe via the active connector

GET  /api/v1/hrms/runs
     Auth: PM|ADMIN
     → [{ id, type, status, recordsPulled, startedAt, completedAt, errorMessage }]
     Last 20 hrms_sync_runs ordered by startedAt DESC.

POST /api/v1/hrms/sync?type=DELTA|FULL
     Auth: PM|ADMIN
     Notes: DELTA syncs from yesterday forward, FULL from 3 months back; both pull
            employee directory + leaves + WFH + balances + attendance per mapped user.
     → { status, recordsPulled, syncedAt }

GET  /api/v1/hrms/leaves?status=&from=&to=
     Auth: isAuthenticated
     → [{ id, hrmsEmpId, hrmsLeaveId, name, av, color,
           leaveType, from, to, days, status, syncedAt }]

GET  /api/v1/hrms/wfh?status=&from=&to=
     Auth: isAuthenticated
     → [{ id, hrmsEmpId, hrmsWfhId, name, av, color,
           wfhDate, wfhType, status, reason, syncedAt }]
     wfhType values: FULL_DAY | HALF_DAY_AM | HALF_DAY_PM
     Default (no params): upcoming WFH records (wfhDate >= today).

GET  /api/v1/hrms/balances?userId=
     Auth: isAuthenticated
     → [{ id, hrmsEmpId, name, leaveType, totalDays, takenDays, pendingDays, remainingDays, syncedAt }]

GET  /api/v1/hrms/attendance?from=&to=&userId=
     Auth: isAuthenticated
     → [{ id, hrmsEmpId, name, date, checkIn, checkOut, workingHours, status }]

GET  /api/v1/hrms/employees
     Auth: PM|ADMIN
     → [{ id, name, email, role, av, color, hrmsEmpId, mapped }]

PATCH /api/v1/hrms/employees/{id}/emp-id
     Auth: ADMIN
     Body: { hrmsEmpId }
     → { ok: true, hrmsEmpId }

POST /api/v1/hrms/webhook
     Unauthenticated; verified by HMAC-SHA256 over the raw body using the
     `webhookSecret` setting and the connector's signature header
     (Darwinbox: X-Darwin-Signature). Fails closed when unconfigured.
```

Schema (V91): `hrms_config(id, provider_key, settings jsonb, enabled, created_at,
updated_at, updated_by)` — existing `darwinbox_config` rows migrated in with
`provider_key='darwinbox'`, then dropped; `darwin_sync_runs` renamed to
`hrms_sync_runs`. `attendance_records` / `leave_records` / `wfh_records` /
`leave_balances` are provider-agnostic and unchanged (their `darwin_emp_id` /
`darwin_leave_id` / `darwin_wfh_id` columns keep their historical names).

### 5.17 Portfolios API *(new v9.0)*

```
GET  /api/v1/portfolios/{id}/summary
     Auth: isAuthenticated
     → { id, name, projectCount, totalCrs, openBugs }
```

### 5.12 Integrations API *(new v4.0)*

```
GET  /api/v1/admin/integrations/slack
     Auth: ADMIN
     → SlackConfigResponse {
         workspaceName, maskedBotToken,  // last 4 chars only e.g. "...ABCD"
         defaultChannel, hasSigningSecret: boolean, updatedAt
       }

PUT  /api/v1/admin/integrations/slack
     Auth: ADMIN
     Body: { workspaceName, botToken, signingSecret, defaultChannel }
     Notes: Tokens encrypted with AES-256 before storage. Returns masked response.
     → SlackConfigResponse

POST /api/v1/admin/integrations/slack/test
     Auth: ADMIN
     Body: { channelId?, message?: string }   // defaults to default_channel and a canned test message
     Notes: Calls SlackService.sendMessage() directly (bypasses HITL — admin test only).
     → { ok: boolean, ts: string, error?: string }

GET  /api/v1/admin/integrations/slack/channels?page=&size=
     Auth: ADMIN
     → Page<SlackChannelMappingResponse> {
         projectId, projectName, channelId, channelName, updatedAt
       }

PUT  /api/v1/admin/integrations/slack/channels/{projectId}
     Auth: ADMIN
     Body: { channelId }
     Notes: SlackService.getChannelInfo(channelId) called to validate and fetch channelName.
     → SlackChannelMappingResponse

DELETE /api/v1/admin/integrations/slack/channels/{projectId}
     Auth: ADMIN
     → 204
```

### 5.18 Snapshots API *(new — Snapshot Reporting Agent, 2026-06-28)*

Slack-triggered captures of the Radar page filtered by portfolio · lens · project. The link returned by the controller doubles as the progress page (frontend polls `/status`).

```
POST /api/v1/snapshots
     Auth: isAuthenticated
     Body: { kind?: "RADAR" (default), portfolioId?: long, lens: string, projectId?: long }
     Notes:
       - `lens` is required and is a role token (LEADERSHIP|ENGINEERING|PM|CSM|REVENUE).
       - Coalescing happens here:
           1. Cache hit if a READY row exists for the same fingerprint within
              snapshot.cache-ttl-seconds (default 300) → returns that row's id.
           2. Otherwise INSERT PENDING + @Async render. Concurrent identical
              submits collide on partial unique index uq_snapshot_inflight
              and the catch-block returns the in-flight row's id.
       - Fingerprint = first 16 hex chars of SHA-256(userId:portfolioId:lens:projectId:kind).
     → { id, state: "PENDING"|"RUNNING"|"READY", fromCache: boolean, dedup: boolean }

GET  /api/v1/snapshots/{id}/status
     Auth: isAuthenticated; controller asserts snapshot.userId == authUser.id || role == 'ADMIN'
     → 200 {
         id, state,
         etaSeconds?: int,           // present while PENDING/RUNNING
         downloadPng?: string,       // /api/v1/snapshots/{id}/png, present only when READY
         downloadPdf?: string,       //                                ditto
         error?: string              // present only when FAILED
       }
     → 404 if no such snapshot · 403 if not owner and not ADMIN

GET  /api/v1/snapshots/{id}/png
     Auth: isAuthenticated + same ownership rule as /status
     → 200 image/png  with Content-Disposition: attachment; filename="orbit-snapshot-{id}.png"
     → 409 if state != READY · 410 if expires_at < now · 404 if missing · 403 if forbidden

GET  /api/v1/snapshots/{id}/pdf
     Same shape, returns application/pdf with filename "orbit-snapshot-{id}.pdf".
```

#### Sidecar contract (internal — not part of the public API)

```
POST {snapshot.sidecar.url}/render
     Body: {
       targetUrl: "http://<frontend>/radar?snapshot=1&portfolio=…&lens=…&project=…",
       jwt:       "<5-min snapshot-scope JWT>",
       viewport:  { w: 1440, h: 900 },
       formats:   ["png", "pdf"],
       waitForSelector: "[data-snapshot-ready=\"true\"]",
       timeoutMs: 20000
     }
     → { png: base64, pdf: base64, renderMs }
```

The sidecar appends `&token=<jwt>` to `targetUrl` itself; `frontend/src/api/client.ts` reads the token only when `?snapshot=1` is present, so it cannot leak to a normal browser load.

### 5.19 Prod-Bug Routing API *(new — 2026-07-01)*

Admin surface for the shared prod-bug pool feature (`V80`). All endpoints under `/api/v1/admin/prod-bug-routing`. Reads = `ADMIN` + `HEAD_PJM`. Mutations = `ADMIN` only.

```
GET  /config
     → [{ projectId, projectName, isSharedProdBugs, clientCodeField, quarantinedOpen }]
     Every project marked as a shared pool + the global open-quarantine count.

PUT  /config/{projectId}                              (ADMIN)
     Body: { isSharedProdBugs: boolean, clientCodeField?: string }
     Notes: shared=true without clientCodeField → 400. shared=false clears clientCodeField.
     → 200 { projectId, projectName, isSharedProdBugs, clientCodeField, quarantinedOpen }
     → 404 if project missing

Also settable at create time via POST /api/v1/admin/projects
     Body: { name, portfolioId, jiraProjectKeys?, clientId?, isSharedProdBugs?, clientCodeField? }
     Notes: clientId is optional when isSharedProdBugs=true (routing decides
     per bug). Both fields flow through to the same `projects` columns.

GET  /clients
     → [{ clientId, clientName, code, hasCode }]
     Every client + whether its `clients.code` is populated. Feeds the admin
     "which clients still need a code?" list.

POST /clients/{clientId}/code                         (ADMIN)
     Body: { code: string }
     Notes: uppercased on write; blank rejected; app-layer duplicate check
     (matches the V80 partial unique index) — response includes the
     conflicting client name on collision.
     → 200 { clientId, clientName, code, hasCode: true }
     → 400 blank / duplicate · 404 client missing

GET  /quarantine?page=&size=                          (ADMIN + HEAD_PJM)
     Paged (default size 50, capped at 200). Ordered by last_seen_at DESC.
     → { content: [{ id, jiraKey, jiraSummary, rawClientCode, reason, seenAt, lastSeenAt }],
         totalElements, totalPages, page, size }

POST /quarantine/{id}/resolve                         (ADMIN)
     Body: { note?: string, assignClientCode?: string }
     Notes: when `assignClientCode` is provided the linked JiraIssue's
     client_id is retro-attributed in the same transaction so the bug
     appears in that client's rollups immediately (no wait for next sync).
     → 200 { id, jiraKey, resolvedAt, resolvedBy, resolutionNote, assignedClientCode }
     → 400 unknown assign code · 400 already-resolved · 404 missing

POST /backfill/{projectId}                            (ADMIN)
     Full Jira re-sync via ProdBugBackfillService — every issue in the shared
     project flows through ProdBugRoutingService, so historical rows get
     client_id rewritten and quarantine rows seeded.
     → 200 { status: "Success", type: "Full", issuesProcessed, durationMs, source: "BACKFILL" }
     → 400 if project isn't marked shared · 404 if project missing
```

Kill-switch: `orbit.prod-bug-routing.enabled` (default `true`, env `ORBIT_PROD_BUG_ROUTING_ENABLED`). When `false`, `JiraSyncService.isRouting(project)` returns `false` for every project regardless of the DB flag — the sync path is unchanged.

### 5.21 AM Dashboard API *(new — 2026-07-10, Account Management persona)*

Backend: `AmDashboardController` (+ `stage_sla_targets` V82, `StageSlaTarget`/`StageSlaTargetRepository`, AM queries in `JiraIssueRepository`). Frontend: `features/radar/am/` (AmHome renders when Orbitter persona = AM).

```
GET /api/v1/am/summary?portfolioId&type          open CRs (excl. Hold) · clientHold · client count
GET /api/v1/am/stage-matrix?portfolioId&type     clients × lifecycle stages: count/avg aging per cell,
                                                 %-within-SLA vs stage_sla_targets (untargeted stages unscored)
GET /api/v1/am/owner-matrix?portfolioId&type     assignee × stage counts (Unassigned bucket; SM/PjM split
                                                 awaits Jira field mapping)
GET /api/v1/am/prod-trend?portfolioId&months     created[]/closed[] per month · openNow · openBySeverity
                &from&to                         (V3: optional yyyy-MM window for quarter/custom filters)
GET /api/v1/am/clients?portfolioId               scorecard: open CRs (+BAU/Launch split), clientId, avg aging,
                                                 open prod by severity
GET /api/v1/am/crs?filters&page&size             Page<drill row> — every matrix/tile click lands here

── V3 reforms (2026-07-14) ────────────────────────────────────────────────────
GET /api/v1/am/pod-score                         POD benchmarking: score = SLA adherence % vs absolute stage
                                                 targets (interim until CSAT; NOT min-max normalized), rank,
                                                 slaBreached/Tracked, openBau/LaunchCrs, prodOpen+bySeverity
GET /api/v1/am/prod-weekly?from&to&portfolioId   W1–W4 created/resolved per month + running open line that
                                                 ends at openNow (week = day-of-month //7)
GET /api/v1/am/owner-share?portfolioId&type      owner → open-CR count (donut source; top-9+Others client-side)
GET /api/v1/am/client/{id}/overview              master-page Overview: KPI counts, BAU/Launch/hold splits,
                                                 prod by severity, SLA adherence + Breached·Near·Met
                                                 (near = <25% of stage window remaining), per-stage rows
GET /api/v1/am/client/{id}/dh-metrics            real-now delivery-health: lead[]/throughput[]/incidents[]
                ?months&type                     per month, stage-SLA compliance %, aging buckets
                                                 (0–15/16–30/31–60/60+), RAG-banded pillar scores (92/62/32;
                                                 pred = null until sprint feeds land)
```

type = LAUNCH|BAU matches `projects.ops_model` (contains-match so launch+bau counts in both). "Open CR" = issueType CR with lifecycleStage ∉ (Released, Closed, Invalid, Resolved, Canceled); stages "Hold"/"On Hold" are normalised into the single Hold bucket (untracked, counted as clientHold). Sections are flag-gated `section.radar.am.*`; csat/velocity/owners-sm/adoption seeded NONE in V82 until their data sources exist. V83 seeds `section.client.dh.*` metric-card flags + `section.client.milestones` at NONE.

```
── Widget-parity Wave 1 (2026-07-15) ──────────────────────────────────────────
GET /api/v1/am/csat-drill?portfolioId            W11 — per client: work-type rows (Launch · CRs / Launch ·
                                                 UAT Bugs / BAU · CRs / Prod Bugs) split Backlog / In
                                                 Progress / Closed by lifecycle stage buckets. No CSAT feed
                                                 needed; opened from the POD card "CSAT L/B → mix" link.
GET /api/v1/am/settings                          dh pillar weights (default 40/35/25) + adoptionUrl
PUT /api/v1/am/settings                          ADMIN only; weights must sum to 100 (am_settings V84, id=1)
GET /api/v1/accounts/{projectId}/sprint-scope    W18 — CRs (open + delivered ≤60d) grouped into phases
                                                 Solutioning → Development → QA → Production release →
                                                 Delivered via AccountDetailService.trackerPhase(stage);
                                                 rows carry ageDays/targetDays + badge on-track|delayed|
                                                 on-hold|delivered; sprint tag null until F3 sprint sync.
```

Wave 1 payload additions: `/am/clients` rows carry `projectId` (client → first active project; AM tile click → `/accounts/{projectId}`, the client master page — mock §4.3). `/am/client/{id}/dh-metrics` adds `reopened[]` (% of month's resolved CRs with reopen_count>0 — series exists but stays out of the quality pillar and behind `section.client.dh.reopened` until the Wave 3 changelog sync writes reopen_count) and `vsPodAvg {speed,quality,pred,clients}` (same pillar computation averaged over the POD's clients). `findResolvedCrRowsForClient` row shape is now (createdAt, resolvedAt, opsModel, reopenCount).

**Client master page = `AccountDetailPage` (`/accounts/:projectId`)**, 6 tabs: Overview · Delivery Speed · Delivery Quality · Delivery Predictability · Teams · Account Workbench (W13). Pillar tabs render `features/accounts/DhPillarPane` (health ring weighted by am_settings, ⚙ admin weights modal, 6m/3m + All/Launch/BAU filters, MetricCard trends from dh-metrics arrays, backlog-aging SegmentBar; no-feed metrics stay flag-gated with "pending" cards). Overview = KPI row (CSAT · Open CRs · Utilization · Prod Bugs — one-fact-one-place, mandays strip and worklist columns retired) + sentiment card + SLA & BAU card (adherence, Breached·Near·Met, last UAT sign-off, last go-live, engagement pending, adoption link from am_settings) + stage-SLA table (drills) + Sprint Scope (flag `section.client.sprint-scope` = ALL) + summary tiles + the data-backed extras (weekly status, release calendar, risks, prod list, support ops, wins, governance — beyond-mock, kept deliberately).

**V84 stage-target audit fix (W7/W8 closure):** V82's seeds used mock-vocabulary stage names that don't occur in the live Jira taxonomy, so nothing was SLA-tracked on real data (pod-score `slaTracked: 0`). V84 seeds the same mock buckets (Intake/Solutioning 30 · Approval 15 · Dev 45 · UAT 21 · Prod-readiness 15) onto the observed stage names (BRD awaited, In dev, In QA, QA Review*, UAT in progress, Fixed, …). Hold/On Hold/Rejected stay untracked. `stage_sla_targets` remains admin-editable — new Jira stages need a row to be SLA-scored.

Cross-widget reconciliation guard: `AmDashboardControllerTest.summaryMatrixAndOwnerShareTotalsReconcile` asserts summary(openCrs+clientHold) == stage-matrix total == Σ clientTotals == Σ stage totals == owner-share total, so widget counts can never drift apart silently.

```
── Widget-parity Wave 2 (2026-07-15, F1/F2/F4) ─────────────────────────────────
GET /api/v1/am/owner-share?dim=assignee|sm|pjm   W9/W10 — SM / PjM donuts read jira_issues.sm_owner /
                                                 pjm_owner; unmapped jira_config field → {configured:false}
                                                 and the UI shows a setup hint (never wrong assignee data)
GET /api/v1/am/crs?smOwner&pjmOwner              drill filters for the SM/PjM donut slices
```

Wave 2 changes: **W1 POD score** = mock parity — min-max normalized `60% CSAT + 40% SLA adherence` across PODs when CSAT exists (`csatLaunch`/`csatBau` in the payload; POD CSAT = simple avg of its clients' non-null values); PODs without CSAT fall back to absolute SLA adherence %, `scoreBasis` says which. **W3 health chips** — `/am/clients` rows carry `healthScore` (D3: weighted DH pillar score via am_settings weights, same math as pillar tabs) + per-client thresholds; scorecard sort-by-health (worst first). **F1** — `clients.csat_launch/csat_bau/engagement_score` (V85, admin-entered via Admin → Clients modal; `PUT /admin/clients/{id}`); client overview returns `csat` (avg of halves), `csatLaunch/csatBau`, `engagementScore` (SLA & BAU card). **F2** — `jira_config.story_points_field/sprint_field/sm_field/pjm_field` (V85, edited in Jira Sync → Field mapping card, `GET/PUT /jira-sync/config`); `jira_issues.story_points/sm_owner/pjm_owner/current_sprint_id/current_sprint_name` populated by `integration/jira/JiraFieldMapper` (shared by JQL sync and webhook; handles Cloud sprint-object arrays and legacy greenhopper toString lines, attribute order-independent; blank mapping = feature dark, never wrong data). JiraSyncService now resolves jira_config once per run (hoisted out of the page loop). **F4/W2** — adoption deep link lives in `am_settings.adoption_url`; AM home Adoption card deep-links when set, admins get an inline URL editor, non-admins see nothing until set (flag `section.radar.am.adoption` = ALL; `section.radar.am.owners-sm` flag deleted — the SM/PjM sections self-manage their unconfigured state). Client names are trimmed in AM rollups and the drill matches `TRIM(client.name)` — live data contains trailing-space duplicates.

```
── Widget-parity Wave 3 (2026-07-15, F3 sprint + changelog sync) ──────────────
GET  /api/v1/am/velocity?portfolioId&sprints     W5 — last N sprints {label, state, live, committed,
                                                 delivered, pct, unpointedCount, approx, breakdown} +
                                                 velocitySoS {pct, delta}; dataAvailable:false until
                                                 sprint sync has data. pod-score + /am/clients rows also
                                                 carry velocitySoS.
GET  /api/v1/am/client/{id}/milestones           W16 — done/upcoming rows auto-derived from sprints
POST /api/v1/jira-sync/backfill-changelog        ADMIN; async, resumable (cursor jira_issues.changelog_
                                                 synced_at, 200-issue slices, 429 Retry-After honored),
                                                 recorded as JiraSyncRun syncType=ChangelogBackfill
GET  /api/v1/jira-sync/backfill-status           {running, pendingIssues, processedThisRun, lastError?}
GET  /api/v1/jira-sync/runs?page&size            PM/ADMIN — page envelope {content, page, size, totalPages,
                                                 totalElements}; rows {id, time, startedAt, completedAt,
                                                 type, issues, status, dur, durationMs, errorMessage,
                                                 projectId, projectName (batch lookup, no N+1),
                                                 triggeredBy, totalExpected, processedSoFar,
                                                 pending = max(0, expected − processed),
                                                 projectScope[], currentProject}; progress/scope fields
                                                 null on historical rows
```

**Jira sync observability (V99):** `jira_sync_runs` gains `project_id` (no FK — runs survive project deletion), `triggered_by` (user email; `system` when unauthenticated, `scheduler` for the cron), `total_expected` / `processed_so_far` (live progress: approximate-count at run start + per-page flush outside any wrapping transaction so pollers see commits immediately), `project_scope` (ordered comma-separated project names stamped at run start) and `current_project` (advances with the loop; cleared on Success, kept on Failed so the row shows where the run died). `total_expected` comes from `POST /rest/api/3/search/approximate-count` — best-effort (null on failure) and ORDER BY is stripped first (`withoutOrderBy`) because the endpoint rejects it. A standing delta cron (`orbit.jira.delta-sync-cron`, default every 30 min; kill-switch `orbit.jira.delta-sync-enabled`) triggers `trigger("delta", null, "scheduler")`, guarded: a Running Full/Delta run younger than 2h blocks the tick and a zero-duration `Skipped` row naming the blocker is recorded instead (`recordSkippedTick`) — stale Running rows (crashed JVMs) never silence the scheduler, and manual triggers stay unguarded on purpose. `@Scheduled` entrypoints that call sibling `@Transactional` methods on `this` carry `@Transactional` themselves (`SprintSyncService.refresh`) — self-invocation bypasses the CGLIB proxy, making the inner annotation invisible on the scheduled path.

**F3 pipeline** (`service/sync/`): `IssueTransitionService` writes the `issue_transitions` ledger (status / sprint / story-point changes; dedup on UNIQUE(issue_id, changelog_id, field_type)) and RECOMPUTES `first_in_progress_at` (first in-progress transition) + `reopen_count` (closed→non-closed transitions) from the full ledger — replay-safe, order-independent. `SprintIngestService` upserts `sprints` from the issue Sprint-field payload (no Board API) and maintains `sprint_issues.added_at/removed_at` from Sprint-field changelog diffs (comma-separated id lists; earliest add / latest remove win). `SprintSyncService` (`@Scheduled orbit.jira.sprint-sync-cron:0 15 * * * *`, dark until sprint_field mapped) refreshes active/future sprint metadata via `/rest/agile/1.0/sprint/{id}` and on a future→active flip snapshots members' SP into `committed_story_points` (D4: member ≤ start+15 min grace = committed; pre-rollout sprints never get a snapshot → `approx:true` in payloads). `ChangelogBackfillService` pages `GET /rest/api/3/issue/{key}/changelog` per pending issue. `VelocityService` is the read side: delivered = SP resolved inside the sprint window while member; spillover% = committed-not-delivered ÷ committed; scope-change% = SP added after start ÷ committed; commitment = delivered∩committed ÷ committed. Webhook now parses the previously-ignored `payload.changelog` block and re-ingests the Sprint field per event.

`GET /jira-sync/field-mappings` now returns real rows (F3 step 8 — was hardcoded demo data whose keys didn't match the table renderer, which read as an empty table): seven built-in mappings (incl. the standard reporter → `jira_issues.reporter_name`/`reporter_email`, V98) plus the six configurable custom fields from jira_config (incl. `developer_field` → `jira_issues.developer_name`, V98), badge "Not mapped" until set. The JQL sync's requested-field list is derived from all configured mappings (`JiraSyncService.requestedFields`) — `/search/jql` only returns requested fields, so a configured custom field can never be silently absent from sync results. The Custom-field-mappings editor card is shared by both Jira admin surfaces — Jira Sync → Field mapping and Admin → Integrations → Jira (the latter duplicates the former's tabs; card exported from `features/jira-sync/JiraSyncPage`).

**dh-metrics additions (Wave 3):** `cycle[]` (resolved − first_in_progress, null until backfill has data — data presence beats the `section.client.dh.cycle` flag in the UI), `predMetrics {commitmentPct, spilloverPct, scopeChangePct, sprints, approx}` over the last ≤6 closed sprints, and `pillars.pred` (RAG-banded avg of the three) — the Predictability tab renders real cards when `predMetrics.dataAvailable`. Account sprint-scope rows now carry the current sprint tag + client-side sprint filter. Flags `section.radar.am.velocity` + `section.client.milestones` = ALL (sections self-manage their awaiting-data state).

**Backfill completion (2026-07-16):** the full changelog backfill drained all 13,835 issues (81.5k transitions, 13k issues with history; 1,953 issues carry reopen_count > 0, 10,552 have first_in_progress_at). With reopen_count now written, the quality pillar is `avgScore(incidents, reopened)` — reopened score = band(current-month reopened %, 3, 8, low), null when the month has no completed work — and the Reopened card renders data-first in `DhPillarPane` (same pattern as cycle); `section.client.dh.reopened` was deleted per the release convention. All outbound sync RestTemplates (`ChangelogBackfillService`, `SprintSyncService`, `JiraSyncService`, the HRMS connector — now `DarwinboxHrmsConnector`) now come from `integration/OutboundHttp` (10s connect / 60s read) after a stalled Jira response with the default infinite timeouts froze a backfill run mid-slice.

**Mock-parity wave (2026-07-16):** Radar persona tiles adopt the POD-selector accent pattern for the active state (teal background + white text — the previous `T.ink` background was invisible in dark mode). Scorecard "CR list" navigates to `/cr?clientId=` (CR board shows the scoped client in its header + clear-filter; the drill modal remains for other widgets). New `design/components/Breadcrumbs.tsx` composite (mock `crumbs`: Role ／ POD ／ page; optional `breadcrumbs` prop on `PageHeader`) renders on the account master page (replacing "← Back to Orbitter"), client master page, and scoped CR board. Client master parity per handoff §5: hero card header ({name, "{POD} POD · RAG word · Client master view", engagement select + Export PDF} — RAG appears only here), Account snapshot widget removed (unique commercial metadata folded into the hero sub-line), sentiment card = mock's three rows (Schedule confidence · Client sentiment · Escalations open), Overview order = KPI → sentiment+SLA → Milestones → Stage SLA → **Timeline & Health (release calendar, never hidden)** → Sprint Scope → Risk Register → CRs & Prod tiles → documented extras (Weekly status, prod open list, Support ops, Wins, Governance). API: `/accounts/{id}` health block adds `scheduleConfidence` (banded slip probability: High <30, Medium ≤60, Low >60), `slipProbabilityPct`, `escalationsOpen` (open alerts on the project — `AlertRepository.countByProjectIdAndStatus`); `/am/client/{id}/overview` adds `releaseCalendar` (releases across the client's active projects, −14d…+2m) so `/clients/:clientId` renders Timeline & Health too.

**Mock-parity wave 2 (2026-07-16):** `velocitySoS` skips closed sprints with zero committed SP (a fully-unpointed latest sprint used to blank the POD card); `predictability()` adds `perSprint[{label, commitmentPct, spilloverPct, scopeChangePct}]` and the Predictability cards render per-sprint TrendBars. CR board = mock spec: filters stage/client/POD/SM/PjM/type (all URL params; `findCrsFiltered` joins `project.portfolio` + ops-model LIKE for type; `/cr/filter-options` serves SM/PjM selects) and ten mock columns (CR·Client·Description·Status·Stage·POD·SM·PjM·Type·Aging) in list + CSV. Bug triage accepts `?tab=uat|prod`. Client master: Stage-SLA title carries the open count and stage rows navigate to `/cr?clientId&stage` (modal retired); sprint-scope rows = mock ss-row (ticket·summary·sprint·badge·target date derived from stage target) with an 8-row per-phase cap + "show all"; the five beyond-mock widgets (Weekly status, prod open list, Support ops, Wins, Governance) are removed from Overview (data + API untouched); pillar header = mock line "pillar score X · weight Y% · vs POD avg Z · configure weights ⚙"; every metric card drills via an "open items" link (speed/pred → CR board, reopened → `/bugs?tab=uat`, incidents → `/bugs?tab=prod`); backlog-aging card spans the grid (`1/-1`); the four no-source quality/pred cards (leakage/CFR/rework/release-success) no longer render (their NONE flags stay for when feeds land). Workbench tab = mock three cards fed by a new `workbench` block on `/accounts/{id}` (thisWeek/nextWeek/attention from resolved-in-7d counts, release calendar, UAT cycles, Hold/awaited stages, open alerts). Teams tab = mock pill-toggle tables with inline edit through the existing `PUT /accounts/{id}/team`; V87 adds `internal_tech_lead/qa_lead/support_mgr`. Export PDF now opens `/accounts/:projectId/report` — a paper-white Delivery Report rendered from the configurable default template (V88 `export_templates` — named to avoid the legacy `report_templates` table; `GET/PUT /api/v1/report-templates*`, ADMIN edits sections/order in-page; report data composes the existing `/accounts/{id}` + `/am/client/{id}/overview` payloads).

**Metric-correctness floor — SLA canonicalization (2026-07-17):** the SLA met/near/breached concept was computed three different ways — 2-bucket (`age ≤ target`) in the stage matrix and POD score, 3-bucket (`met`/`near = last 25% of window`/`breached`) only in the client overview — so the same client could show different SLA numbers on different surfaces. New `service/am/SlaBucketService` is now the single classifier: `breached = age > target`, `near = age > 0.75·target`, `met` = rest, `adherencePct = met / (met+near+breached)`, untracked stages (no target) skipped. All four surfaces route through it — `/am/stage-matrix` (now emits per-stage `near`; `withinSlaPct` = adherence), `/am/client/{id}/overview` (headline + per-stage rows), `/am/pod-score` (now emits `slaMet/slaNear/slaBreached`; `slaBreached` is strict-breached, no longer `tracked−met`), `/am/client/{id}/dh-metrics` (`slaCompliancePct`). **Behaviour shift (intended):** CRs whose age sits in the last 25% of the window count as `near`, not `met`, so stage-matrix/POD/dh-metrics SLA %s drop where that band is populated (the old numbers were the bug). Pinned by `SlaBucketServiceTest` (boundary classification) and `AmDashboardControllerTest.slaBucketsReconcileAcrossMatrixOverviewAndPodScore` (same CRs fed through all three repo shapes must report identical met/near/breached) — the reconciliation harness that makes silent cross-widget divergence a failing test. Prod-bug counts were also audited: the `findProdBugRowsForClient(clientId, since)` 1-year window is *inert* for open-bug counting (`createdAt ≥ since OR resolvedAt IS NULL` always returns open bugs), so all prod-bug surfaces already agree — no change needed.

**SLA-breach escalation loop — Wave 2 detection (2026-07-17):** `service/agent/SlaBreachSweep` is the standing detector. `@Scheduled(cron=${orbit.agents.sla-escalation.cron:0 30 * * * *})` (gated by `orbit.agents.sla-escalation.enabled`, default false), it queries open CRs (`JiraIssueRepository.findOpenCrsForEscalation` — entities, LEFT JOIN FETCH client, for the issue key + owner fields), classifies each against `stage_sla_targets` via the canonical `SlaBucketService`, and returns `Candidate`s that are BREACHED (or NEAR within `near-margin-pct` of target) and not proposed within `cooldown-days` per the `cr_escalation` dedup ledger (V90, `CrEscalation`/`CrEscalationRepository`). Owner (assignee → sm → pjm) availability is correlated from `LeaveRecordRepository`; owner-on-leave breach ⇒ urgency HIGH. The detection core `findCandidates(now)` is pure and unit-tested (`SlaBreachSweepTest`). Config: `orbit.agents.sla-escalation.{enabled,cron,cooldown-days,near-margin-pct}`.

**Wave 3 — HITL send.** When `enabled`, `sweep()` turns each candidate into a proposal: it drafts a Slack nudge via `AiGateway` (deterministic template fallback if the AI provider is unavailable, so a draft failure never blocks a real escalation) and calls `AgentRuntime.execute(def, projectId, "CRON", {message, issueKey, client, urgency}, "SCHEDULED")` on a find-or-seeded system `AgentDefinition` ("SLA Breach Escalation", tools = `slack.send_channel`, `requiresHitl=true`). Because `AgentRuntime` runs a fixed tool pipeline (no AI tool-planning) and passes `inputContext` straight through as the tool args, the def needs only the one HITL-gated tool — the sweep does the detection and drafting itself. `slack.send_channel` is therefore queued `AWAITING_HITL` and **never sent by the sweep**; `HitlAwaitingEvent` → `SlackHitlBridge` posts the approval card and only `HitlApprovalService.approve` executes the send + writes `AgentDecisionLog` (rule 3 upheld — no `send_notification`/outbound outside a HITL gate). The `cr_escalation` ledger row is stamped at proposal time for dedup. End-to-end coverage is layered: `SlaBreachSweepTest` pins the sweep→runtime handoff (drafted message in, ledger stamped, nothing sent, disabled-guard, AI-fallback); `AgentRuntimeTest` pins queued-not-sent + `AWAITING_HITL`; `HitlApprovalServiceTest` pins approve→execute+decision-log; `SlackHitlBridgeTest` pins the card. **Enabled + full-context E2E:** `orbit.agents.sla-escalation.enabled=true`; `SlaEscalationLoopIntegrationTest` (@SpringBootTest, test profile) drives the real wiring — seeds a breaching CR, runs `sweep()`, asserts `slack.send_channel` is queued `AWAITING_HITL` with the drafted message and *not* sent, then calls the real `HitlApprovalService.approve` and asserts the send fires + an `APPROVED` `AgentDecisionLog` is written (SlackService + AiGatewayService `@MockBean`, so no real workspace/AI call). A *live* run additionally needs the operator's Slack bot token/signing secret in the DB and a project→channel mapping. **Deviation from the filed approach:** the def carries only `slack.send_channel` (not the get_stakeholder_contacts/get_leave_today read tools) because the runtime has no AI planning loop — listing read tools would just re-run them with the send args; the sweep already gathers that context.

### 5.20 Feature Flags API *(new — 2026-07-08, Controlled Release)*

Backend: `FeatureFlagController` + `FeatureFlagRepository` + `domain/config/FeatureFlag` (V81). Frontend consumer: `app/featureFlags.tsx`. Admin UI: Features Control Center (`/flags`).

```
GET  /api/v1/feature-flags/effective            isAuthenticated()
     → { "<flagKey>": boolean, ... }            resolved for the caller:
                                                ALL → true · NONE → false ·
                                                PILOT → pilot_emails contains caller email
                                                (case-insensitive) · ADMIN role → always true.
                                                Keys absent from the map are treated as ON
                                                by the frontend (unknown key = visible).

GET  /api/v1/admin/feature-flags?page=&size=    hasRole('ADMIN')
     → Page<{ id, flagKey, description, audience, pilotEmails[], updatedBy, updatedAt }>
       sorted by flagKey (locked convention #2: every list endpoint paginates)

POST /api/v1/admin/feature-flags                hasRole('ADMIN')
     { flagKey, description?, audience, pilotEmails? }
     → upsert by flagKey (no separate create/update). audience ∈ ALL|PILOT|NONE
       (400 otherwise). pilotEmails trimmed, lowercased, deduped.
       updated_by stamped from the authenticated principal.

DELETE /api/v1/admin/feature-flags/{id}         hasRole('ADMIN')
     → 204. Deleting a flag RELEASES the feature (unknown key = visible).
```

Gating semantics (frontend):
- `screen.<navId>` — `layout/nav.tsx#visibleNav` filters the launcher tile / avatar-menu item; `Shell` wraps the route: flag off → `<Navigate to="/radar" replace/>`. Role gate and flag gate apply independently.
- `section.<page>.<name>` — `<Feature flag="…" fallback?>` around the component.
- `useFlags()` caches `/effective` via react-query (`staleTime` 60s); the Features Control Center invalidates both the admin list and the effective map on every mutation.

---

## 6. Service Layer Key Algorithms (v4.0)

### 6.1 ManDayConsumptionService.compute()
```
For project P in period [start, end]:

  assignments = findActive(projectId, start, end)
  burnedDays = 0

  for each assignment A:
    effectiveStart = max(A.startDate, start)
    effectiveEnd   = min(A.endDate ?? end, end)
    workingDays    = countWorkingDays(effectiveStart, effectiveEnd)
    leaveDays      = countApprovedLeave(A.developerId, effectiveStart, effectiveEnd)
    effectiveDays  = (workingDays - leaveDays) × (A.allocationPct / 100.0)
    burnedDays    += effectiveDays × (project.dailyRateHours / 8.0)

  remaining       = budget.purchasedDays - burnedDays
  burnRate7d      = compute7DayRollingAverage(projectId)
  exhaustionDate  = remaining > 0 ? today + (remaining / burnRate7d) : today

  // alert_threshold_pct indicator for burn chart
  thresholdDays   = budget.purchasedDays × (budget.alertThresholdPct / 100.0)
  // returned as thresholdDays — rendered as vertical line on SVG chart
```

### 6.2 AlertEngine.evaluateIssue()
```
Fires after every Jira sync event and capacity change.

alerts = []

// No owner
if issue.assignee == null:
    alerts += alert(NO_OWNER, sourceAgent="AlertEngine")

// Milestone breach
for each milestone where targetDate < today AND actualDate == null:
    alerts += alert(MILESTONE_BREACH, milestone, sourceAgent="AlertEngine")

// Stage aging
if timeSinceLastTransition(issue) > staleThreshold[issue.stage]:
    alerts += alert(STATUS_AGING, sourceAgent="AlertEngine")

// SLA (prod bugs only)
if issue.type == PROD_BUG:
    sla = findRule(issue.clientId, issue.severity)
    remaining = computeBusinessHoursRemaining(issue, sla)
    if remaining < 0:   alerts += alert(SLA_BREACHED, sourceAgent="AlertEngine")
    elif remaining < 2: alerts += alert(SLA_AT_RISK,  sourceAgent="AlertEngine")

// Hold aging
if issue.stage == HOLD AND daysSince(issue.lastTransition) > holdThreshold:
    alerts += alert(HOLD_AGING, sourceAgent="DeliveryIntelligenceAgent")

// Capacity overload
assigneeLoad = bandwidthService.getUtilization(issue.assignee)
if assigneeLoad > 85:
    alerts += alert(CAPACITY_OVERLOAD, sourceAgent="AlertEngine")

// Budget risk (delegated to ManDayForecastAgent)
// Raised by ManDayForecastAgent.dailyRun() — not duplicated here

return deduplicate(alerts)  // suppress re-raise of identical open alert
// sourceAgent field stored on every alert for display in Alert Center
```

### 6.3 RiskScoringService.scoreProject()
```
v1 — Heuristic (synchronous, runs on every dashboard request):

  score = 100
  score -= criticalAlerts × 15
  score -= highAlerts × 8
  score -= overdueMilestones × 10
  score -= tbcMilestones × 3
  score -= slaBreaches × 12
  score -= holdAgingDays > 7 ? 8 : 0
  score -= manDayBurnPct > 80 ? 10 : manDayBurnPct > 60 ? 5 : 0
  score -= avgUtilization > 85 ? 8 : 0

  slipProbability = sigmoid((100 - score) / 15)   // 0.0–1.0
  riskLevel = score >= 80 ? GREEN : score >= 60 ? AMBER : RED

  // Append to risk_score_history[14] rolling array (persisted in Redis)
  // Used by sidebar heat strip and project card sparkline

v2 — LLM augmentation (async, runs after v1):
  context = { recentMilestones, similarPastProjects, teamChanges, burnTrend }
  llmResponse = llm.complete(RISK_ANALYST_PROMPT, context)
  → updates alert.aiExplanation + cockpit insight text
```

### 6.4 BertTriageService.classifyIssue()
```
Fires at Jira sync time for new or re-opened issues.

v1 — GPT-4o few-shot:
  prompt = fewShotExamples + "\nNew issue: " + issue.summary
  response = llm.complete(TRIAGE_CLASSIFIER_PROMPT)
  → { severity: "P1", suggestedOwner: "kavya.t" }

v2 — Fine-tuned BERT (Sprint 6+):
  embedding = bert.encode(issue.summary + " " + issue.description[:200])
  prediction = bertClassifier.predict(embedding)
  → { severity, suggestedOwner, confidence }

In both cases:
  issue.bertSuggestedSeverity = prediction.severity
  issue.bertSuggestedOwner    = prediction.suggestedOwner
  issue.bertSuggestionAccepted = null  // pending PJM decision
  // Stored immediately; shown as AiSuggestionChip in Bug Triage + CR Board
```

### 6.5 EmbeddingService.search()
```
At sync time:
  text = issue.summary + " " + issue.description[:500]
  embedding = openai.embed(text)
  upsert issue_embeddings(issue_id, embedding, embedded_at=now)

At query time (agent tool: orbit.semantic_search_past_issues):
  queryEmbedding = openai.embed(query)
  results = pgvector.search(
    SELECT issue_key, summary, lifecycle_stage,
           1 - (embedding <=> $1) AS similarity
    FROM   issue_embeddings ie
    JOIN   jira_issues ji ON ji.id = ie.issue_id
    WHERE  1 - (embedding <=> $1) > 0.82
    ORDER  BY similarity DESC
    LIMIT  5,
    queryEmbedding
  )
```

### 6.6 ClientHealthService.computeScore()
```
// Uses per-client thresholds from clients table

score = 100
score -= criticalAlerts(clientId) × 12
score -= slaBreaches(clientId) × 15
score -= tbcMilestones(clientId) × 3
score -= burnPct > 85 ? 10 : burnPct > 70 ? 5 : 0
score -= openP0P1Bugs(clientId) × 8

level = score >= client.healthGreenThreshold ? GREEN
      : score >= client.healthAmberThreshold ? AMBER
      : RED
      
// Colour in Client Backlog health card reads this computed level, not a hardcoded scale
```

### 6.7 AiGatewayService.complete()
```
For every LLM call:
  response = openai.chat(model, messages, maxTokens)
  
  tokensUsed = response.usage.promptTokens + response.usage.completionTokens
  
  // Log to agent_runs if called from agent context
  if (agentRunContext != null):
    agentRun.tokensUsed += tokensUsed
  
  // Accumulate daily cost (for CostSummaryResponse)
  redisClient.incrby("cost:tokens:" + today, tokensUsed)
  
  return response.choices[0].message.content
```

### 6.8 AgentRuntime.execute() *(v5.0 hardening)*
```
Input: AgentDefinition def, AgentRunContext ctx

Key v5.0 changes:
  inputContext: objectMapper.writeValueAsString(inputContextMap)   // Jackson JSON, not Map.toString()
  tool result:  objectMapper.writeValueAsString(resultMap)         // same — avoids jsonb type rejection
  All jsonb-mapped String fields annotated @JdbcTypeCode(SqlTypes.JSON) on entity to tell
  Hibernate 6 to bind as JSON type rather than varchar (Postgres always returns HTTP 200 for
  chat.postMessage; only the body "ok" field indicates real success)

1. Create AgentRun record (status=RUNNING)
2. Load short-term memory: redis.get("agent:{def.id}:project:{ctx.projectId}:*")
3. Load long-term memory: agentMemoryService.search(def.id, ctx.projectId, ctx.eventSummary, limit=5)
4. Build LLM context: [systemPrompt] + [shortTermSummary] + [longTermFacts] + [eventContext]

5. ReAct loop (max 10 iterations):
   a. llm.complete(context + conversationHistory) → toolCallOrFinalAnswer
   b. if finalAnswer: break
   c. toolCall = parse(llmResponse)
   d. tool = toolRegistry.get(toolCall.toolId)   // throws if not in def.allowedTools
   e. if tool.isHitlRequired():
        proposal = hitlGateway.pause(runId, toolCall)   // blocks virtual thread
        if proposal.outcome == REJECTED: log and break
        if proposal.outcome == EDITED:   toolCall.args = proposal.editedArgs
   f. result = tool.execute(toolCall.args, ctx)
   g. agentToolCallService.log(runId, toolCall, result, hitlOutcome)
   h. conversationHistory += [toolCall, result]

6. agentMemoryService.writeShortTerm(def.id, ctx.projectId, "last_run_summary", outputSummary)
7. agentRun.status = COMPLETED; agentRun.completedAt = now; agentRun.tokensUsed = total
8. stomp.send("/topic/copilot/{sessionId}", {type:"done"})
```

### 6.9 AgentMemoryService *(new v4.0)*
```
Short-term (Redis, 7d TTL):
  write(agentId, projectId, key, value):
    redis.setex("agent:{agentId}:project:{projectId}:{key}", 7*86400, serialize(value))

  read(agentId, projectId, key):
    return redis.get("agent:{agentId}:project:{projectId}:{key}")

Long-term (agent_memory table, permanent unless expires_at set):
  persist(agentId, projectId, key, value, memoryType):
    embedding = openai.embed(value)
    upsert agent_memory(agentId, projectId, memKey=key, memValue=value,
                        memoryType=memoryType, embedding=embedding)

  search(agentId, projectId, query, limit=5):
    queryEmbedding = openai.embed(query)
    return pgvector.search(
      SELECT mem_key, mem_value, memory_type,
             1 - (embedding <=> $1) AS similarity
      FROM   agent_memory
      WHERE  agent_id = $2
        AND  project_id = $3
        AND  (expires_at IS NULL OR expires_at > NOW())
        AND  1 - (embedding <=> $1) > 0.75
      ORDER  BY similarity DESC
      LIMIT  $4,
      [queryEmbedding, agentId, projectId, limit]
    )

Memory is agent+project scoped — each agent has independent memory per project.
```

### 6.10 NotificationSchedulerService.evaluate() *(new v5.0)*
```
Runs every 30 minutes via @Scheduled(fixedDelay = 30 * 60 * 1000).

today = LocalDate.now()
active = phaseStatusRepo.findAllActive()   // end_date IS NOT NULL AND status != COMPLETED

for each PhaseStatus ps in active:
  daysUntil = DAYS.between(today, ps.endDate)

  if daysUntil == 2 AND today != ps.lastNotifiedT2:
    sendReminder(ps, "T2_REMINDER", buildPreDueMessage(ps, 2))
    ps.lastNotifiedT2 = today; save(ps)

  else if daysUntil == 1 AND today != ps.lastNotifiedT1:
    sendReminder(ps, "T1_REMINDER", buildPreDueMessage(ps, 1))
    ps.lastNotifiedT1 = today; save(ps)

  else if daysUntil == 0 AND NOT ps.ddayNotified:
    sendReminder(ps, "DDAY_PROMPT", buildDdayMessage(ps))
    ps.ddayNotified = true; save(ps)

  else if daysUntil < 0:
    if ps.status NOT IN (COMPLETED, DELAYED_SYSTEM):
      ps.status = DELAYED_SYSTEM; save(ps)
      createOverdueAlert(ps, -daysUntil)    // severity=critical if ≥3d late
      sendEscalationToProjectChannel(ps, -daysUntil)

sendReminder(ps, eventType, message):
  // Dedup: skip if same phase_status_id + eventType sent within 23h
  if eventRepo.existsByPhaseStatusIdAndEventTypeAndSentAtAfter(ps.id, eventType, now - 23h):
    return
  slackUserId = slack.resolveSlackUserId(ps.assigneeEmail)  // email → Slack user ID
  if slackUserId present: slack.sendDm(slackUserId, message)
  log NotificationEvent(phaseStatusId, eventType, recipientEmail, sentAt)
```

### 6.11 SlackSendChannelTool.execute() *(v5.0 rewrite)*
```
Channel resolution (in priority order):
  1. args.get("channel") — if non-blank and not "general"
  2. slack.resolveChannel(ctx.projectId)
     a. check slack_project_channels for this project
     b. fall back to slack_config.default_channel
  3. If none found → return {ok:false, error:"no_channel_configured", hint:"..."}

Message building:
  if args.get("message") is non-blank → use it
  else → "[Agent #{agentId}] Orbit agent run triggered. Check the Orbit dashboard..."

Execution:
  result = slack.sendToChannelDetailed(channel, message)
  → {ok, channel, ts, error?}     // ts = Slack message timestamp (non-empty on success)
  
Note: slack.sendToChannelDetailed() parses the Slack API response body and checks ok field.
Slack always returns HTTP 200; errors are in {ok:false, error:"channel_not_found"} in body.
```

### 6.12 ProdBugRoutingService.route() *(new — 2026-07-01)*

Called by `JiraSyncService.upsertIssue` when the sync target project is marked as a shared prod-bug pool (`is_shared_prod_bugs=true`) and the global kill-switch `orbit.prod-bug-routing.enabled` is `true`. Mutates the in-memory `JiraIssue` and manages the `prod_bug_quarantine` row; caller persists the issue.

```
route(JiraIssue issue, Object rawFieldValue, Project sharedProject):

  Step 1 — normalise the raw custom-field payload.
  Jira returns select-list fields as {"value":"ACME"} and free-text fields
  as a plain string. normaliseCode(raw):
    - String     → trim; empty → null
    - Map        → prefer m.get("value"), fall back to m.get("name"); trim; empty → null
    - anything   → raw.toString(); trim; empty → null

  Step 2 — dispatch.
  IF code is null:
      issue.client = null
      recordQuarantine(issue, null, MISSING_CODE)
      return

  match = clients.findActiveByCodeIgnoreCase(code)   // UPPER(TRIM(...)), active = true only
  // active-only — a code left on a retired duplicate client row
  // quarantines (UNKNOWN_CODE) instead of silently routing bugs to a hidden client.
  // Same rule on the admin assign path: resolveQuarantine rejects assignClientCode
  // that matches only an inactive client.
  IF match is present:
      issue.client = match
      clearQuarantineIfPresent(issue.issueKey)  // auto-resolve stale row
  ELSE:
      issue.client = null
      recordQuarantine(issue, code, UNKNOWN_CODE)

recordQuarantine(issue, rawCode, reason):
  existing = quarantine.findByJiraKey(issue.issueKey)
  IF existing is present:
      existing.lastSeenAt = now              // seenAt is preserved
      existing.reason     = reason           // may have moved MISSING↔UNKNOWN
      existing.rawClientCode = rawCode
      IF existing.resolvedAt != null:        // admin marked resolved but bug still stuck
          existing.resolvedAt = null         //   → reopen instead of silently dropping
          existing.resolvedBy = null
          existing.resolutionNote = null
      quarantine.save(existing)
      return
  new ProdBugQuarantine with (jiraIssue=issue, jiraKey, rawClientCode=rawCode, reason)
  quarantine.save(new)

clearQuarantineIfPresent(jiraKey):
  existing = quarantine.findByJiraKey(jiraKey)
  IF existing is present AND existing.resolvedAt is null:
      existing.resolvedAt = now
      existing.resolvedBy = "auto:code-now-matches"
      quarantine.save(existing)
  // Admin-resolved rows are NOT overwritten — keep the audit trail intact.
```

Invariants:
- **No bug is dropped.** Every stuck bug lands in `prod_bug_quarantine` with `client_id = NULL`; consumers filtering on `client.id` skip it, global rollups still count it.
- **Idempotency per Jira key.** `jira_key UNIQUE`: re-sync bumps `last_seen_at`, never duplicates.
- **`seenAt` is set once.** First encounter timestamp is preserved for admin triage; `lastSeenAt` tracks recency.
- **Case-insensitive code match.** `findByCodeIgnoreCase` applies `UPPER(TRIM(...))` on both sides; admin UI uppercases on save.

`ProdBugBackfillService.backfill(projectId)` is a thin wrapper: validates the project exists and is marked shared, then calls `JiraSyncService.trigger("full", projectId)`. The routing branch does the work — no separate backfill pass reads existing rows because the raw custom-field value was never persisted pre-flag, so re-hitting Jira is the only correct data source.

### 6.13 Dashboard caching + bulk-context scoring *(2026-09-03 perf wave)*

In-process Caffeine caches (`CacheConfig`) for the three heavy dashboard aggregates: `radar` (`DashboardService.getRadar()`), `clients-list` (`ClientOverviewService.clientOverviews()`), `portfolio-dashboard` (`PortfolioDashboardService.dashboard(portfolioId)`, keyed by id, nulls never cached). TTL `orbit.cache.dashboard-ttl-seconds` (default 90s) bounds staleness for inputs that change outside the sync path; two eviction triggers clear everything immediately:

- `@EvictsDashboardCaches` — composed `@CacheEvict(allEntries)` on every mutating endpoint whose data feeds a cached payload (Admin stages/clients/projects CRUD, client thresholds, portfolio CRUD/membership). **Rule: any new mutation feeding a cached payload must carry this annotation.**
- `JiraDataChangedEvent` — published by `JiraSyncService` after a successful sync run; `CacheInvalidationListener` clears all three caches.

Behind the caches, scoring is bulk-context so a miss costs a fixed query count regardless of project count:

- `ProjectHealthService.preloadContext(projects)` → `HealthContext` (8 grouped queries: P0/P1 bugs, SLA breaches, open/hold CRs, open UAT bugs, latest burned days via `findTop14PerProject` window query, purchased days, profile weights); `compute(p, ctx)` / `healthPctAll(projects)` score from the maps.
- `RiskScoringService.preload(projects, devList)` → `RiskContext` (~10 grouped queries: project+client alert counts, TBC/overdue milestones, SLA breaches, holding CRs, budgets, snapshots newest-first, UAT blockers by client, team load); `score(p, ctx)` is the context variant, `score(p)` self-preloads for single-project callers.
- `GET /api/v1/portfolios/{id}/dashboard` returns `{summary, kpis, accounts, exceptions}` in one response; the four legacy endpoints serve slices of the same cached payload, and `RadarPage` fires one request instead of four.

---

## 7. Agent Memory Design (v4.0)

| Memory type | Storage | Key | Contents | Lifetime |
|---|---|---|---|---|
| Short-term (current run) | JVM heap (ReAct loop state) | N/A | Conversation history, tool outputs for current run | Single agent run |
| Short-term (cross-run) | Redis, 7-day TTL | `agent:{id}:project:{pid}:{key}` | Last run output, last reminded items, countdown state | 7 days; refreshed on each run |
| Long-term (project facts) | `agent_project_summaries` | project_id | Rolling ~500-token project summary for LLM context | Updated after each run; permanent |
| Long-term (agent memory) | `agent_memory` + pgvector | agent_id + project_id + mem_key | Cross-run decisions, outcome patterns, repeated issues | Permanent or until expires_at |
| Episodic (decisions) | `agent_decision_log` | N/A | HITL proposals, approvals, rejections, outcome_note, tokens_used | Permanent (audit) |
| Semantic (issues) | `issue_embeddings` (pgvector) | issue_id | Embedding of every issue summary | Updated at every sync |

Every LLM agent run receives (in order):
1. System prompt from `agent_definitions.system_prompt`
2. Long-term project summary (`agent_project_summaries.summary_text`, ~500 tokens)
3. Top-5 semantically relevant long-term memories via `memory.search`
4. Short-term Redis snapshot of last run output
5. Current event context + live tool call outputs

---

## 8. WebSocket Streaming Protocol (v4.0)

```
Client connects: WS /ws  (JWT in handshake Sec-WebSocket-Protocol header)
Subscribes:      /topic/copilot/{sessionId}
                 /topic/reports/{userId}   (for report generation progress)
                 /topic/standup/{projectId} (for countdown updates)
                 /topic/agent-runs/{runId} (for Agents → Live tab — v10.2)

POST /api/v1/copilot/message → 202 Accepted

Server (virtual thread per session):
  1. Build agent context (load memory, resolve tool manifest)
  2. Stream LLM tokens via STOMP:

  {type:"token",       content:"Morning Priya..."}
  → Frontend: append to bubble, show TypingIndicator.tsx (3-dot animated)

  {type:"tool_call",   name:"orbit.semantic_search_past_issues", args:{query:"hold aging NX"}}
  → Frontend: render ToolCallTrace (monospace block, amber left border)

  {type:"tool_result", name:"orbit.semantic_search_past_issues", summary:"NX-741: same pattern Sprint 38"}
  → Frontend: render ToolCallTrace result

  {type:"proposal",    id:"p-123", action:"slack.send_channel",
   payload:{channelId:"C04ABCDE123", message:"Reminder: NX-884 overdue 18d"}}
  → Frontend: render HITL button row (Approve / Edit / Reject)
  
  {type:"done"}
  → Frontend: hide TypingIndicator

Report generation completion:
  {type:"report_ready", reportId:"42", title:"Weekly delivery — Nexus Corp"}
  → Frontend: remove loading spinner, highlight report row in table

Standup countdown (broadcast every 60s):
  {type:"standup_countdown", projectId:"1", secondsRemaining:1440}
  → Frontend: update "Auto-posts in Xm" chip in StandupCard

Agent run live log (v10.2 — emitted from AgentRuntime per tool boundary):
  {type:"agent_run_step",
   runId:42, stepId:99,
   toolName:"slack.send_channel",
   status:"STARTED"|"COMPLETED"|"AWAITING_HITL"|"FAILED",
   message:"posted to #orbit-hitl", ts:"2026-06-26T17:30:00Z"}
  → Frontend: append log line in Agents → Detail → Live tab.
  HITL-status events render an inline "Approve here" button that opens
  the same modal as the cross-agent approval inbox.

  Subscribe on Live-tab mount; unsubscribe on tab change / page leave.
  Subscription is server-authoritative — JWT role must be ADMIN or HEAD_PJM
  (enforced by SecurityConfig WS interceptor; same gate as the REST runs API).
```

### 8.1 AgentRunStepEvent (server-side)

```java
public record AgentRunStepEvent(
    Long runId,
    Long stepId,
    String toolName,
    String status,   // STARTED | COMPLETED | AWAITING_HITL | FAILED
    String message,
    Instant ts
) {}
```

Published by `AgentRuntime` before/after each tool call via `ApplicationEventPublisher`. Listener `AgentRunStreamBridge` (`@EventListener @Async`) forwards to STOMP topic `/topic/agent-runs/{runId}` via `SimpMessagingTemplate`. Bridge swallows downstream errors so a Slack/WS outage never rolls back the agent run (mirrors `SlackHitlBridge`). The existing `HitlAwaitingEvent` is also republished on the same topic so the Live tab sees `AWAITING_HITL` without polling.

### 8.2 Admin agents REST contract (consolidated v10.2)

All paths under `/api/v1/admin/agents/**`. `@PreAuthorize` enforces RBAC on every endpoint.

| Verb | Path | Owner | Purpose | Role |
|---|---|---|---|---|
| GET | `/admin/agents` | `AgentDefinitionController` | List agent definitions | ADMIN, HEAD_PJM |
| POST | `/admin/agents` | `AgentDefinitionController` | Create definition | ADMIN |
| PUT | `/admin/agents/{id}` | `AgentDefinitionController` | Update definition (blocks system agents from rename) | ADMIN |
| PATCH | `/admin/agents/{id}/toggle` | `AgentDefinitionController` | Enable / disable | ADMIN |
| DELETE | `/admin/agents/{id}` | `AgentDefinitionController` | Delete (blocked for system agents) | ADMIN |
| POST | `/admin/agents/{id}/test-run` | `AgentDefinitionController` | Synchronous test invocation (source=MANUAL_TEST) | ADMIN |
| GET | `/admin/agents/{id}/runs` | `AgentDefinitionController` | Paginated runs for one agent | ADMIN, HEAD_PJM |
| GET | `/admin/agents/{id}/runs/{runId}/steps` | `AgentDefinitionController` | Tool calls for a specific run | ADMIN, HEAD_PJM |
| GET | `/admin/agents/tools` | `AgentDefinitionController` | List available tools from `ToolRegistry` | ADMIN |
| GET | `/admin/agents/runs` | `AgentLogsController` | Cross-agent run listing (filterable by `agentId`, `status`) | ADMIN, HEAD_PJM |
| GET | `/admin/agents/runs/pending-hitl` | `AgentLogsController` | HITL inbox (polled every 30s by the Pending Approvals tab) | ADMIN, HEAD_PJM |
| GET | `/admin/agents/runs/{runId}/steps` | `AgentLogsController` | Enriched steps (includes agent + run context) | ADMIN, HEAD_PJM |
| POST | `/admin/agents/runs/{runId}/steps/{stepId}/approve` | `AgentLogsController` | Approve a HITL tool call (optional `editedPayload`) | ADMIN, HEAD_PJM |
| POST | `/admin/agents/runs/{runId}/steps/{stepId}/reject` | `AgentLogsController` | Reject (requires `reason` → `outcome_note`) | ADMIN, HEAD_PJM |

---

## 9. Jira Integration (v4.0)

### Webhook processing (idempotent)
```
POST /api/jira/webhook
  Header: X-Hub-Signature-256 — HMAC-SHA256 validated against JIRA_WEBHOOK_SECRET

Processing:
  1. Check jira_webhook_events.webhook_id for duplicate → skip if seen
  2. Parse: issue_created | issue_updated | issue_deleted | comment_created
  3. Extract key, changelog, transitions, field values
  4. Upsert JiraIssue (smart merge — preserve Orbit manual annotations)
  5. Append IssueTransition records if status changed
  6. Recompute milestones if date custom fields changed
  7. Run BertTriageService.classifyIssue(issue)  [async]
  8. Fire AlertEngine.evaluateIssue(issue)        [async]
  9. Fire DeliveryIntelligenceAgent.onIssueEvent  [async, if risk change detected]
  10. Return 200 immediately — steps 7–9 are @Async virtual threads
```

### Webhook config
```
Endpoint:   https://akki-pjm.internal/api/jira/webhook
Events:     issue:created, issue:updated, issue:deleted, comment:created
Validation: HMAC-SHA256 · X-Hub-Signature-256 header
Retry:      3 retries · exponential backoff · dead-letter queue after 3 failures
Projects:   Per-project — configurable in Jira Sync Health > Webhook Config tab
Displayed:  Endpoint URL (read-only) · secret (masked) · event list · project scope
```

### Field mapping
| Jira field | Orbit field | Notes |
|---|---|---|
| `fields.issuetype.name` | `issueType` | CR/Bug/UAT/Task mapping in Lifecycle Mapping admin |
| `fields.customfield_XXXXX` (BRD date) | `milestone.BRD.targetDate` | Per-client custom field ID |
| `fields.customfield_XXXXX` (FSD date) | `milestone.FSD.targetDate` | Per-client custom field ID |
| `changelog.transitions` | `IssueTransition[]` | Full history for SLA calculation |
| `fields.environment` | `environment` | PROD \| UAT \| STAGING |
| `fields.priority.name` | `severity` | P0=Critical, P1=High, P2=Medium, P3=Low |
| `fields.fixVersions[0].name` | `fixVersion` | Release target |
| `fields.customfield_holdReason` | `holdReason` | Hold reason text — per-client field config |

---

## 10. Flyway Migration Plan (V16 → V83)

```
V16__create_clients.sql                    # clients table with per-client health thresholds
V17__add_client_id_to_projects.sql
V18__create_man_day_budgets.sql            # includes alert_threshold_pct
V19__create_jira_issues.sql               # includes bert_suggested_* columns
V20__create_issue_milestones.sql
V21__create_issue_transitions.sql
V22__create_issue_notes.sql
V23__create_sla_rules.sql
V24__create_uat_cycles.sql                 # full spec: cycle_number, sign_off_status, env_snapshot
V25__create_alerts.sql                     # includes source_agent, follow_up_date
V26__create_alert_actions.sql
V27__create_app_users.sql
V28__create_report_templates.sql
V29__create_generated_reports.sql
V30__create_report_schedules.sql           # full spec: cron_expression, recipients[]
V31__create_man_day_snapshots.sql
V32__create_jira_webhook_events.sql
V33__create_lifecycle_mappings.sql
V34__create_agent_tables.sql              # agent_project_summaries, agent_decision_log (with outcome_note, tokens_used)
V35__create_issue_embeddings.sql          # pgvector
V36__create_indexes_phase2.sql
V37__create_client_dependencies.sql       # dependency tracker
V38__create_uat_sign_offs.sql             # uat sign-off records (separate from uat_cycles for audit)
# --- Agent Framework (Sprint 8) ---
V39__reserved.sql                          # (placeholder — V39–V48 reserved for any S7 patches)
V49__create_slack_config.sql               # NEW v4.0: slack_config + slack_project_channels
V50__create_agent_definitions.sql          # NEW v4.0: agent_definitions (Agent Builder persistence)
V51__create_agent_memory.sql               # NEW v4.0: agent_memory with pgvector embedding column
V52__create_agent_runs.sql                 # NEW v4.0: agent_runs execution log
V53__create_agent_tool_calls.sql           # NEW v4.0: agent_tool_calls per-run tool audit
# --- Notifications Engine (Sprint 9) ---
V54__create_notification_rules.sql         # NEW v5.0: notification_rules (23 seeded: T-2/T-1/D-Day/digest)
V55__create_escalation_config.sql          # NEW v5.0: escalation_config + global_spoc_config
V56__create_phase_statuses.sql             # NEW v5.0: phase_statuses per project × phase
V57__create_notification_events.sql        # NEW v5.0: notification_events send log
V58__alerts_add_phase.sql                  # NEW v5.0: ALTER alerts ADD phase, days_overdue
V59__update_role_screens_integrations.sql  # NEW v5.0: replace jira/darwin screen IDs with integrations
V60__add_agent_logs_screen.sql             # NEW v6.0/v7.0: add agent-builder to role_screen_config (ADMIN-only post-v9.0 rename)
V61__wfh_records.sql                       # NEW v8.0: wfh_records table (wfh_date, wfh_type, status, reason) + 5 seed rows (merged screen)
V62__app_users_add_can_edit_budget.sql     # NEW v9.0: can_edit_budget column for fine-grained PM budget-edit permission
# … V63–V78 cover v10.x portfolio/SLA/Darwinbox/account-detail/agents/Slack onboarding work …
V79__agent_snapshots.sql                   # NEW 2026-06-28: snapshot-reporting-agent schema
V80__prod_bug_routing.sql                  # NEW 2026-07-01: shared prod-bug pool → per-client fan-out schema
V81__feature_flags.sql                     # NEW 2026-07-08: controlled-release feature flags + flags screen for ADMIN
V82__am_dashboard.sql                      # NEW 2026-07-10: stage_sla_targets + AM role + section.radar.am.* flags at NONE
V83__am_v3_reforms.sql                     # NEW 2026-07-14: section.client.dh.* / milestones / sprint-scope flags at NONE (client master page)
V84__am_settings_and_stage_targets.sql     # NEW 2026-07-15: am_settings single row (dh weights 40/35/25 + adoption_url) + stage_sla_targets seeds for the live Jira stage vocabulary (W7/W8 audit fix)
V85__am_csat_and_jira_field_mappings.sql   # NEW 2026-07-15: clients.csat_launch/csat_bau/engagement_score (F1) · jira_config SP/sprint/SM/PjM field ids + jira_issues mapped columns (F2)
V86__jira_sprint_changelog_sync.sql        # NEW 2026-07-15: sprints + sprint_issues tables · issue_transitions → generic field ledger (changelog dedup) · jira_issues.first_in_progress_at/changelog_synced_at (F3)
V90__cr_escalation_ledger.sql              # NEW 2026-07-17: cr_escalation dedup ledger (issue_key PK, last_proposed_at, last_outcome, decision_log_id) for the SLA-breach escalation loop
# … V91–V93 cover HRMS connector config, seed-data removal, team role labels …
V94__enable_pg_stat_statements.sql         # best-effort CREATE EXTENSION pg_stat_statements (RAISE NOTICE when not superuser)
V95__stage_catalog.sql                     # stages table — catalog of delivery stage names/order/category; seeded from legacy default list + lifecycle_mappings + observed jira_issues.lifecycle_stage
V96__canonicalize_lifecycle.sql            # terminal Jira statuses (Rejected/Cancelled/Canceled/Released to Production) → Closed stage + resolved_at backfill from the transitions ledger; lifecycle_mappings.issue_type relabelled to the canonical vocabulary (dedupe shadowed legacy rows)
V97__drop_redundant_transition_index.sql   # drop idx_tr_issue — pure prefix of idx_issue_transitions_field(issue_id, field_type, transitioned_at) from V86
V98__developer_and_reporter_fields.sql     # jira_config.developer_field (user-picker mapping, V85 pattern) + jira_issues.developer_name/reporter_name/reporter_email (standard reporter object)
V99__jira_sync_run_observability.sql       # jira_sync_runs.project_id/triggered_by/total_expected/processed_so_far/project_scope/current_project + (sync_type, started_at DESC) and webhook received_at indexes
V100__role_chart_config.sql                # role_screen_config.chart_config JSONB (per-role dashboard chart prefs: chartType/breakdownChartType/palette/runtimeToggle) + section.charts.config flag row (audience ALL)
```

### V79 `agent_snapshots` *(new — Snapshot Reporting Agent)*

```sql
CREATE TABLE agent_snapshots (
  id            BIGSERIAL PRIMARY KEY,
  agent_run_id  BIGINT REFERENCES agent_runs(id),
  user_id       BIGINT NOT NULL REFERENCES app_users(id),
  dedup_key     VARCHAR(64) NOT NULL,                -- SHA-256 of (userId:portfolioId:lens:projectId:kind), 16 hex chars
  kind          VARCHAR(32) NOT NULL,                -- "RADAR" today; reserved for future page types
  portfolio_id  BIGINT REFERENCES portfolios(id),
  lens          VARCHAR(32) NOT NULL,                -- LEADERSHIP|ENGINEERING|PM|CSM|REVENUE
  project_id    BIGINT REFERENCES projects(id),
  state         VARCHAR(16) NOT NULL,                -- PENDING|RUNNING|READY|FAILED
  png_path      TEXT,
  pdf_path      TEXT,
  error_message TEXT,
  created_at    TIMESTAMP NOT NULL DEFAULT now(),
  completed_at  TIMESTAMP,
  expires_at    TIMESTAMP NOT NULL DEFAULT (now() + INTERVAL '7 days')
);

CREATE INDEX  idx_snapshot_user_created
  ON agent_snapshots (user_id, created_at DESC);

CREATE INDEX  idx_snapshot_dedup_completed
  ON agent_snapshots (dedup_key, completed_at DESC)
  WHERE state = 'READY';

-- Idempotency at the DB layer: at most one PENDING/RUNNING row per dedup_key.
-- Concurrent submits collide here; SnapshotService catches DataIntegrityViolationException
-- and converts it into a dedup-hit lookup.
CREATE UNIQUE INDEX uq_snapshot_inflight
  ON agent_snapshots (dedup_key)
  WHERE state IN ('PENDING', 'RUNNING');
```

### V80 `prod_bug_routing` *(new — Shared Prod-Bug Routing)*

```sql
-- Case-sensitive-per-row uniqueness — clients may still have code=NULL (opt-in field).
-- Partial index skips NULL / empty rows so the constraint applies only to populated codes.
CREATE UNIQUE INDEX uq_clients_code
  ON clients (code)
  WHERE code IS NOT NULL AND code <> '';

-- Per-project routing config. is_shared_prod_bugs defaults FALSE so behaviour is
-- unchanged for every existing project; admins opt-in per pool.
ALTER TABLE projects
  ADD COLUMN is_shared_prod_bugs BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN client_code_field   VARCHAR(64);

-- One row per stuck Jira issue. jira_key UNIQUE makes upsert-on-resync trivial.
-- last_seen_at bumped on repeated syncs of the same bug (see ProdBugRoutingService).
CREATE TABLE prod_bug_quarantine (
  id               BIGSERIAL PRIMARY KEY,
  jira_issue_id    BIGINT REFERENCES jira_issues(id) ON DELETE CASCADE,
  jira_key         VARCHAR(64) UNIQUE NOT NULL,
  raw_client_code  VARCHAR(64),
  reason           VARCHAR(32) NOT NULL,           -- MISSING_CODE | UNKNOWN_CODE
  seen_at          TIMESTAMP NOT NULL DEFAULT now(),
  last_seen_at     TIMESTAMP NOT NULL DEFAULT now(),
  resolved_at      TIMESTAMP,
  resolved_by      VARCHAR(255),
  resolution_note  TEXT
);

-- Fast "open work queue" lookup — the only query the admin UI runs.
CREATE INDEX idx_quarantine_open
  ON prod_bug_quarantine (last_seen_at DESC)
  WHERE resolved_at IS NULL;
```

### V81 `feature_flags` *(new — Controlled Release, 2026-07-08)*

```sql
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

-- Expose the flag-management screen (Features Control Center) to admins.
UPDATE role_screen_config
  SET screen_ids = screen_ids || ',flags'
  WHERE role_name = 'ADMIN' AND screen_ids NOT LIKE '%flags%';
```

---

## 11. Configuration (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/akki
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate.ddl-auto: validate

akki:
  jira:
    base-url: ${JIRA_BASE_URL}
    email: ${JIRA_EMAIL}
    api-token: ${JIRA_API_TOKEN}
    webhook-secret: ${JIRA_WEBHOOK_SECRET}
    delta-sync-cron: "0 */10 * * * *"

  ai:
    provider: openai                        # openai | anthropic
    openai.api-key: ${OPENAI_API_KEY}
    model: gpt-4o
    embedding-model: text-embedding-3-small
    bert-classifier-enabled: true           # v1: GPT-4o few-shot; v2: fine-tuned model
    max-tokens-per-call: 4096
    agent-enabled: true
    cost-per-1k-tokens: 0.03               # USD, for CostSummaryResponse

  forecast:
    sidecar-url: ${PROPHET_SIDECAR_URL:http://localhost:8001}
    ci-bands: [80, 95]                      # confidence interval levels for chart

  capacity:
    overload-threshold: 85
    busy-threshold: 70
    man-day-warning-pct: 80

# Snapshot Reporting Agent (root namespace — see SnapshotService / RadarSnapshotAgent / sidecar)
snapshot:
  renderer: ${SNAPSHOT_RENDERER:mock}              # mock (default, no sidecar) | http (production)
  sidecar:
    url:        ${SNAPSHOT_SIDECAR_URL:http://snapshot-sidecar:3001}
    timeout-ms: ${SNAPSHOT_TIMEOUT_MS:20000}
  storage:
    root:       ${SNAPSHOT_STORAGE_ROOT:./.snapshots}   # mounted volume in compose
  cache-ttl-seconds:    ${SNAPSHOT_CACHE_TTL_SECONDS:300}    # READY rows reused as cache
  stuck-cutoff-seconds: ${SNAPSHOT_STUCK_CUTOFF_SECONDS:60}  # SnapshotWatchdog: PENDING/RUNNING > N s → FAILED

orbit:
  public-base-url: ${ORBIT_PUBLIC_BASE_URL:http://localhost:3000}  # used to build /snapshots/{id} link DMed to Slack users

  redis:
    dashboard-ttl-seconds: 120
    report-list-ttl-seconds: 300
    risk-history-window-days: 14            # heat strip window
    agent-memory-ttl-days: 7               # NEW v4.0: short-term agent memory TTL

  agents:
    delivery.enabled: true
    standup.cron: "0 0 8 * * MON-FRI"
    standup.auto-post-delay-minutes: 30
    escalation.hold-threshold-days: 5
    escalation.require-hitl: true           # never relaxed — all escalations need approval
    dev-reminder.enabled: true              # NEW v4.0
    dev-reminder.cron: "0 0 9 * * MON-FRI" # NEW v4.0: daily 9am Mon–Fri
    agent-runtime.max-iterations: 10        # NEW v4.0: ReAct loop iteration cap
    agent-runtime.dry-run-skip-hitl: true   # NEW v4.0: test-run bypasses HITL

  client:
    health-default-green-threshold: 80
    health-default-amber-threshold: 60

  slack:                                    # NEW v4.0: Slack integration
    enabled: ${SLACK_ENABLED:false}         # false until configured in Admin > Integrations
    token-encryption-key: ${SLACK_TOKEN_ENC_KEY}  # AES-256 key for encrypting bot_token at rest
    api-base-url: https://slack.com/api    # Slack Web API base
    connect-timeout-ms: 3000
    read-timeout-ms: 5000
```

---

## 12. Sprint Delivery Plan (v5.0)

| Sprint | Backend | Frontend |
|---|---|---|
| **S1 (2w)** | DB V16–V24 · Jira webhook + delta sync · CR/bug/alert API · JWT auth skeleton | Sidebar (collapsible) · CR Board with pagination · Login screen |
| **S2 (2w)** | Alert engine (source_agent + follow_up_date) · Man-day consumption + budget edit API · Client health (per-threshold) | Man-Days screen + Edit Budget modal · Alert Center + assign-with-date · Client Backlog + dependency tracker |
| **S3 (2w)** | Full RBAC guards · Bug API (prod + UAT) · Standup agent v1 + Redis countdown | PJM Cockpit + standup countdown · Bug Triage (prod + UAT tab) · Radar (heuristic scores + heat strips) |
| **S4 (2w)** | DeliveryIntelligenceAgent + pgvector + HITL API · WS streaming (token/proposal/done) · UAT cycles API | Copilot panel (streaming + TypingIndicator) · UAT Tracker standalone · Agent Audit Log (cost summary) |
| **S5 (2w)** | ManDayForecastAgent (Prophet CI bands) · EscalationAgent · ReportDraftingAgent · Report schedule engine | Man-Days forecast run + CI chart · Reports screen + schedules tab + generate WS progress · Admin: Report Schedules |
| **S6 (2w)** | AI exec briefing (LLM) · BERT auto-tag (v1 GPT-4o) · Slip prob v2 (LLM) · Jira sync health full API | Radar AI briefing bullets · AiSuggestionChip on Bug/CR · Jira Sync Health (4 tabs) · Cost summary bar |
| **S7 (1w)** | Load testing · security review · Swagger docs polish | Final UI polish · route guard audit · deployment runbook · handover |
| **S8 (2w)** | DB V49–V53 · AgentTool interface + ToolRegistry · AgentRuntime + HitlGateway · AgentMemoryService · SlackService · AgentDefinition CRUD (§5.11) · Integrations API (§5.12) · DevReminderAgent | Agent Builder (agent list + create/edit modal + tool picker + test-run + run history) · Integrations screen (Slack config + channel mapping) · ClientUpdateAgent skeleton |
| **S9 (2w)** | DB V54–V60 · NotificationSchedulerService (@Scheduled 30-min) · AlertRulesController (§5.13) · PhaseStatusController (§5.14) · SlackService.sendToChannelDetailed() · SlackSendChannelTool channel resolution + message fix · AgentRuntime @JdbcTypeCode fix · testRun steps[] response · toMap() promptTemplate + channelConfig fix · HitlApprovalService (approve/reject: executes tool + writes decision log) · AgentLogsController (§5.15) · AgentDecisionLog @JdbcTypeCode fix · AgentBuilderPage merged with Agent Logs (3-tab Agents screen at /agent-builder; /agent-logs redirects; ADMIN-only access; 64 tests) | Notification Rules admin (4 tabs: Rules · Escalation Matrix · Global SPOCs · Event Log) · Phase Deliveries admin (per-project phase schedule editor) · Integrations page consolidated (Jira + Darwinbox + Slack tabs) · Alert Center phase column + daysOverdue badge · **Agents screen** (/agent-builder — Agents tab · Execution logs tab · Pending approvals tab with HITL approve/reject) · AgentRunHistory component (inline per-agent run history in ▾ Runs toggle) |
| **S10 (1w)** | Security: JWT RS256 · Jira webhook bypass removed · Slack AES-256 token encryption · CORS env-var allowlist · input validation on key endpoints. Bugs: real SLA counts · Copilot hardcoded proposal removed · AgentRuntime tool args passthrough · StandupAgent countdown fix. 16 missing agent tools (§4.1) + DevReminderAgent. V61 `wfh_records` · `WfhRecord` + `WfhRecordRepository` · `DarwinboxConnectorService` WFH loop + `fetchWfhFromApi()` stub · `GET /api/v1/darwin/wfh` (§5.16) | DarwinIntegration 5-tab layout + "WFH this week" stat card · `orbit.get_leave_today` and `orbit.get_upcoming_leaves` return WFH data |
| **S11 (patch)** | V62 `can_edit_budget` on `app_users` · JWT `canEditBudget` claim · budget-edit permission check in `ManDayController` · `portfolioId` filter on CR list + stage-summary · `GET /portfolios/{id}/summary` · `GET /man-days/portfolio-summary` · `JiraIssueRepository` portfolio queries · role rename PM/ENGINEERING throughout | `GlobalCopilotPanel` floating global copilot (Shell-level) · Login tagline "Delivery Command Center" · Sidebar: Orbitter home (no My Today/cockpit) · No Jira Live section |
| **S12 (v10.0)** | V64 SLA defaults seed (P0–P3) + `jira_config.sla_field` · V65 `darwinbox_config`/`attendance_records`/`leave_balances` · V66 `projects.go_live_date`/`projects.health_stage` + `health_profile_weights` (4×6 seeded) · V67 `portfolio_clients` M:N (drops `portfolios.client_id`) · V68 reclassify legacy `PROD_BUG` → `UAT_BUG` · `SlaService` (resolveRule + computeStatus + parseJiraSlaStatus + `@Scheduled` recompute) · `ProjectHealthService` (resolveStage + compute returning `HealthResult(healthPct, stage, signals[])`) · `DarwinboxConnectorService` rewrite (config resolution DB→env · auth modes · response envelope unwrap · snake_case mapping · pagination · employee directory auto-map · 4 sync flows + webhook processing) · `AuthController.googleLogin/googleCallback` · `JiraSyncService.mapIssueType` rule change · package rename `com.gauge.*` → `com.orbit.*` · `BugController` null-safe queries (`countOpenByClientTypeAndSeverityIn`, `countOpenReopenedByClientAndType`, `countOpenUnassignedByClientAndType`) · `BugControllerTest` + `JiraSyncServiceMapIssueTypeTest` | Portfolio Setup multi-select `ClientPicker` · inline "New project" modal scoped to portfolio clients · Account cards stage badge + health bar · POD Health KPI tile (avg of project scores) · Health profiles admin (per-stage weight sliders + sensitivity + deduction preview) · SLA rules admin (defaults + overrides + recompute button + JSM field config) · Darwinbox admin form (DB config + auth type + 7 tabs incl. Attendance, Leave balances) · Bug Triage filter dropdowns (client + severity + SLA state for prod; stage for UAT) + URL deep-link · UAT Tracker client filter · Favicon (inline orbit SVG) · Reactive sidebar collapse (individual `useStore` selectors) · "Open Prod Bugs" tile · Dark-mode CSS custom properties · Google SSO button wired |
| **S13 (v10.1)** | V69 migration: `project_team` (4 internal + 4 client cols), `project_risks` (jira_ticket · risk · received_on · rag · action_end · action_owner · source), `project_releases` (release_date · release_type · label · rag), `projects.ops_model` col. 3 new entities (`ProjectTeam`, `ProjectRisk`, `ProjectRelease`) + 3 repos. `Project.opsModel` field. `JiraIssueRepository.findByProjectIdAndIssueTypeOrderByUpdatedAtDesc` for worklist sourcing. **`AccountDetailService`** orchestrates 7 repos to build single payload (mandays + milestones + weeklyStatus + launchOps/bauOps + productionIssues tracker + 4 worklists + internalTeam/clientTeam + health + riskRegister + releaseCalendar). **`AccountDetailController`**: `GET /accounts/{id}` (auth); `PUT /accounts/{id}/team` (PM/ADMIN upsert); `POST /accounts/{id}/risks` + `DELETE …/risks/{id}` (PM/ADMIN/CSM, cross-project guard on delete); `PUT /accounts/{id}/ops-model` (PM/ADMIN, validates against `launch`/`bau`/`launch+bau` whitelist). `AccountDetailControllerTest` — 10 tests (aggregator delegation, 404 path, team upsert, risk add/delete with cross-project guard, ops-model validation). | New `AccountDetailPage` at `/accounts/:projectId` — sticky header (stage badge + RAG + health % + client/POD/go-live line + ops-model dropdown + Export PDF). Overview tab: 6-card weekly status milestones + computed bullets · 3-card mandays strip (RAG-coloured progress bar + low-buffer warning) · 2-column launch+BAU ops split · risk register table with inline add form (RAG dropdown + dates + owner) and per-row delete · production issues tracker (6 stats + status breakdown + ageing buckets + issue table) · release calendar (cards coloured by type) · 3-column worklists (launch stories · open CRs · prod bugs). Workbench tab with sub-tabs: Team (4 internal + 4 client contact cards) · Health (sentiment/delivery/stability cards + UAT bug table). `@media print` hides `.no-print` elements. `RadarPage` account cards rewired to `/accounts/{id}`. New `Route` in `Shell.tsx`. |

---

## 13. v10.0 Additions — Schema, APIs, Services

### 13.1 New / changed schema (V64–V68)

#### V64 — SLA defaults + Jira SLA field
```sql
-- Seed global defaults (client_id NULL = applies to all clients)
INSERT INTO sla_rules (client_id, severity, response_hours, resolution_hours, include_weekends) VALUES
  (NULL, 'P0',   2.0,   4.0, TRUE),
  (NULL, 'P1',  16.0,  24.0, FALSE),
  (NULL, 'P2',  48.0,  72.0, FALSE),
  (NULL, 'P3', 120.0, 168.0, FALSE);

-- Optional JSM custom field name (e.g. customfield_10020)
ALTER TABLE jira_config ADD COLUMN sla_field VARCHAR(60);
```

`SlaRuleRepository`:
- `findBySeverityAndClientIsNull(severity)` → default rule
- `findBySeverityAndClientId(severity, clientId)` → client override

#### V65 — Darwinbox config + attendance + leave balances
```sql
CREATE TABLE darwinbox_config (
  id BIGSERIAL PRIMARY KEY,
  base_url VARCHAR(255), api_key VARCHAR(2000), company_id VARCHAR(100),
  auth_type VARCHAR(20) DEFAULT 'API_KEY',  -- API_KEY | BEARER | HMAC
  enabled BOOLEAN DEFAULT FALSE,
  sync_cron VARCHAR(60) DEFAULT '0 0 */4 * * *',
  sync_days_ahead INTEGER DEFAULT 90,
  webhook_secret VARCHAR(200),
  updated_at TIMESTAMP, updated_by VARCHAR(150)
);

CREATE TABLE attendance_records (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES app_users(id),
  darwin_emp_id VARCHAR(50) NOT NULL, attendance_date DATE NOT NULL,
  check_in TIME, check_out TIME, working_hours DECIMAL(5,2),
  status VARCHAR(30), synced_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(darwin_emp_id, attendance_date)
);

CREATE TABLE leave_balances (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES app_users(id),
  darwin_emp_id VARCHAR(50) NOT NULL, leave_type VARCHAR(60) NOT NULL,
  total_days DECIMAL(5,1) DEFAULT 0, taken_days DECIMAL(5,1) DEFAULT 0,
  pending_days DECIMAL(5,1) DEFAULT 0, remaining_days DECIMAL(5,1) DEFAULT 0,
  synced_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(darwin_emp_id, leave_type)
);
```

#### V66 — Project life-stage + health weights
```sql
ALTER TABLE projects ADD COLUMN go_live_date DATE;
ALTER TABLE projects ADD COLUMN health_stage VARCHAR(20);  -- NULL = auto-inferred

CREATE TABLE health_profile_weights (
  id BIGSERIAL PRIMARY KEY,
  stage VARCHAR(20) NOT NULL,        -- PRE_LAUNCH | HYPERCARE | STEADY_STATE | AT_RISK
  metric VARCHAR(50) NOT NULL,        -- prod_bug_p0 | prod_bug_p1 | sla_breach |
                                      -- cr_on_hold_pct | uat_bug_count | manday_burn_risk
  weight INTEGER NOT NULL DEFAULT 0,  -- max deduction this metric can contribute (0–100)
  sensitivity DECIMAL(4,2) NOT NULL DEFAULT 1.0,  -- normalisation speed
  UNIQUE(stage, metric)
);
-- Seeds 4 stages × 6 metrics = 24 rows with stage-appropriate weights.
```

#### V67 — Multi-client portfolios (M:N)
```sql
CREATE TABLE portfolio_clients (
  portfolio_id BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
  client_id    BIGINT NOT NULL REFERENCES clients(id)    ON DELETE CASCADE,
  PRIMARY KEY (portfolio_id, client_id)
);

-- Migrate existing single-client associations
INSERT INTO portfolio_clients (portfolio_id, client_id)
SELECT id, client_id FROM portfolios WHERE client_id IS NOT NULL;

-- Drop old FK
ALTER TABLE portfolios DROP COLUMN IF EXISTS client_id;
```

`Portfolio` JPA mapping:
```java
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(name = "portfolio_clients",
  joinColumns = @JoinColumn(name = "portfolio_id"),
  inverseJoinColumns = @JoinColumn(name = "client_id"))
private Set<Client> clients = new LinkedHashSet<>();
```

`PortfolioRepository.findByClientsIdAndActiveTrue(Long clientId)` — derived query that traverses the join table.

#### V68 — Reclassify legacy PROD_BUG → UAT_BUG
```sql
UPDATE jira_issues
SET issue_type = 'UAT_BUG'
WHERE issue_type = 'PROD_BUG'
  AND (jira_status IS NULL
       OR LOWER(jira_status) NOT LIKE '%production%'
       AND LOWER(jira_status) NOT LIKE '%hotfix%'
       AND LOWER(jira_status) NOT LIKE '%outage%'
       AND LOWER(jira_status) NOT LIKE '%sev-1%'
       AND LOWER(jira_status) NOT LIKE '%sev1%');
```

### 13.2 New API contracts

#### Auth: Google SSO
```
GET  /api/v1/auth/google
  → 302 redirect to https://accounts.google.com/o/oauth2/v2/auth?...

GET  /api/v1/auth/google/callback?code={code}
  → exchanges code for id_token via /oauth2/googleapis.com/token
  → calls /tokeninfo for email/name/verification
  → upserts AppUser (default role: PM, generated bcrypt password placeholder)
  → mints Orbit JWT
  → 302 redirect to {FRONTEND_URL}/login?token={jwt}&id={id}&name=...&role=...
```

Error paths: `?error=google_not_configured` · `?error=google_denied` · `?error=unverified` · `?error=oauth_failed`.

#### SLA: Admin endpoints
```
GET    /api/v1/admin/sla-rules                      # list (defaults + overrides combined)
POST   /api/v1/admin/sla-rules                      # create rule
PUT    /api/v1/admin/sla-rules/{id}                 # update rule
DELETE /api/v1/admin/sla-rules/{id}                 # delete rule
POST   /api/v1/admin/sla-rules/recompute            # manual SlaService.recomputeAll()
```

Body shape:
```json
{ "clientId": 5, "sev": "P0", "resp": "2h", "res": "4h", "wk": true }
```
`clientId: null` → global default. `wk` = include weekends.

Jira SLA field is stored on `jira_config.sla_field` and edited via the existing `PUT /api/v1/jira-sync/config`.

#### Stage catalog (V95)

`stages` is the single source of truth for delivery stage names, ordering and colour category — the frontend no longer hardcodes a stage list. Seeded from the legacy default list, existing `lifecycle_mappings` rows (carrying their V72 order/category), and any `jira_issues.lifecycle_stage` value never mapped, so no existing reference is orphaned.

```sql
CREATE TABLE stages (
  id            BIGSERIAL PRIMARY KEY,
  name          VARCHAR(64) NOT NULL UNIQUE,
  display_order INT         NOT NULL DEFAULT 50,
  category      VARCHAR(20) NOT NULL DEFAULT 'in-progress',  -- backlog|in-progress|qa|uat|blocked|ready|released|closed
  updated_by    VARCHAR(120),
  updated_at    TIMESTAMP   NOT NULL DEFAULT now()
);
```

`StageCatalogService` owns the invariants: rename cascades to `lifecycle_mappings.gauge_stage`, `jira_issues.lifecycle_stage` and `stage_sla_targets.stage` in one transaction; delete is refused while any mapping or issue still references the stage; `ensureExists()` is the auto-discover safety net (any stage auto-discovery assigns must exist in the catalog).

```
GET    /api/v1/admin/stages        # [{id, name, displayOrder, category, mappingCount, issueCount}]
POST   /api/v1/admin/stages        # {name, category?, displayOrder?} — ADMIN; order defaults to max+10
PATCH  /api/v1/admin/stages/{id}   # rename/recategorize/reorder — ADMIN; rename cascades
DELETE /api/v1/admin/stages/{id}   # ADMIN; 409 {error, mappingCount, issueCount} while referenced
```

The lifecycle-mapping admin screen reads its stage dropdown from the catalog and manages stages inline (add / rename / recategorize / delete-when-unused).

#### Lifecycle canonicalization (V96)

`lifecycle_mappings.issue_type` uses the canonical sync vocabulary — `CR · PROD_BUG · UAT_BUG · TASK · OTHER` plus the wildcard `ALL`. `POST /admin/lifecycle-mappings` translates legacy display labels via `LifecycleMapping.canonicalType()` ("Bug"→PROD_BUG, "UAT Bug"→UAT_BUG, "Task"→TASK, "All"→ALL) and rejects unknown labels with 400; the save is an upsert keyed on `(jira_status, issue_type)`.

Stage resolution (`JiraSyncService.buildStageMap`/`stageFor`) is dual-keyed `issueType|status` with a bare-status fallback: a type-specific row wins for its own type; `ALL` rows own the bare-status fallback for every other type (deterministic — no insertion-order races); a completely unmapped status passes through raw. `/cr/stages` includes `ALL` rows in every per-type stage list.

Terminal Jira statuses (`Rejected`, `Cancelled`, `Canceled`, `Released to Production`) always land in the `Closed` stage; V96 repairs already-synced rows and backfills `resolved_at` from the `issue_transitions` status ledger (falling back to `updated_at`) so closed rows count in closed-in-period widgets.

#### Health profiles
```
GET   /api/v1/admin/health-profiles
  → { stages: [...], metrics: [...], weights: { PRE_LAUNCH: [...], HYPERCARE: [...], ... } }

PUT   /api/v1/admin/health-profiles/{stage}/{metric}
  body: { weight: 30, sensitivity: 1.5 }

PATCH /api/v1/admin/projects/{id}/stage
  body: { healthStage: "AT_RISK", goLiveDate: "2026-09-15" }
```

#### Portfolios (multi-client)
```
POST /api/v1/portfolios
  body: { "name": "Lending POD", "description": "...", "clientIds": [1, 4, 7] }

PUT  /api/v1/portfolios/{id}
  body: { "clientIds": [1, 4] }   # replaces full set
```

Response always includes:
```json
{
  "id": 5, "name": "Lending POD", "description": "...", "active": true,
  "clientIds":   [1, 4, 7],
  "clientNames": ["Nexus Corp", "Apex Fintech", "Polaris"],
  "clientId":   1,                          // first entry — back-compat
  "clientName": "Nexus Corp"                // first entry — back-compat
}
```

#### HRMS (extended)
```
GET  /api/v1/hrms/config              # provider + settings view (secrets as set/unset flags)
PUT  /api/v1/hrms/config              # save { provider, enabled, settings{...} }
GET  /api/v1/hrms/balances?userId=    # leave balances
GET  /api/v1/hrms/attendance?from=&to=&userId=  # attendance window
POST /api/v1/hrms/webhook             # webhook receiver (unauth) — leave_*/wfh_*/employee_*
```

### 13.3 Service-layer algorithms

#### SlaService.computeStatus(severity, createdAt, client)
```
rule = resolveRule(severity, client)         // client override → global default
elapsed = elapsedHours(createdAt, rule.includeWeekends)  // biz hrs Mon-Fri 9-18 IST unless flagged
if (elapsed >= rule.resolutionHours)  return "Breached"
if (elapsed >= rule.responseHours)    return "At risk"
return "On track"
```

Hourly recompute: `@Scheduled(cron = "0 0 * * * *")` over all open bugs (`issueType IN {PROD_BUG, UAT_BUG}`, `lifecycleStage NOT IN {Closed, Invalid, Resolved, Canceled}`).

JSM integration: `parseJiraSlaStatus(fieldValue)` extracts `ongoingCycle.breached` / `ongoingCycle.remainingTime.millis` from the JSM custom field shape; falls back to computed rules when null/unparseable.

#### ProjectHealthService.compute(project) → HealthResult
```
stage = resolveStage(project)  // manual override → auto-inferred from go_live_date
weights = loadWeightMap(stage) // 6 metrics, weight + sensitivity
penalty = 0
for metric in [prod_bug_p0, prod_bug_p1, sla_breach, cr_on_hold_pct, uat_bug_count, manday_burn_risk]:
  w = weights[metric]
  if w.weight == 0: continue
  raw  = rawMetricValue(metric, project)        // counts / ratios from JiraIssue + ManDay repos
  norm = min(1.0, raw * w.sensitivity)          // normalise to [0..1]
  ded  = norm * w.weight                        // contribute up to `weight` points
  penalty += ded
healthPct = clamp(100 - penalty, 0, 100)
return HealthResult(healthPct, stage, signals)
```

Stage auto-inference:
- `go_live_date` null or future → `PRE_LAUNCH`
- 0–90 days since `go_live_date` → `HYPERCARE`
- 91+ days → `STEADY_STATE`
- `AT_RISK` is always manual (no auto-trigger)

Raw metric definitions (sample):
- `cr_on_hold_pct` = (CRs in `Hold`/`Client Hold`) / (total open CRs); ratio 0–1
- `manday_burn_risk` = `max(0, (burnPct − 80) / 20)`; activates above 80% burn

#### HRMS connector orchestration (HrmsSyncService + DarwinboxHrmsConnector)
```
HrmsSyncService.sync(type):
  1. record HrmsSyncRun IN_PROGRESS
  2. resolve active connector from hrms_config.provider_key via HrmsConnectorFactory
  3. enabled && connector.isConfigured(settings) → connector.sync(settings, type)
     else no-op (count of records already in DB) — graceful degradation
  4. finalize HrmsSyncRun (status, recordsPulled, completedAt)

DarwinboxHrmsConnector.sync(settings, type):
  1. syncEmployeeDirectory()                 // /apiv2/employees paginated; auto-map by email
  2. for each user with darwin_emp_id:
     - upsertLeaves(empId, from, to)         // /apiv2/employees/leavedetails
     - upsertWfh(empId, from, to)            // /apiv2/employees/wfhdetails
     - upsertLeaveBalances(empId)            // /apiv2/employees/leavebalance
     - upsertAttendance(empId, from, to)     // /apiv2/employees/attendance (paginated)
```

All credentials come from the `hrms_config.settings` JSONB (no env fallbacks). Headers built per `authType`:
- `API_KEY`: `x-api-key: {apiKey}`
- `BEARER`:  `Authorization: Bearer {apiKey}`
- `HMAC`:    *(stubbed; uses API_KEY pattern)*

Response unwrap: every `apiPost/apiGet` extracts `response["data"]` as `List<Map>`; returns empty list on any error so a single bad endpoint doesn't break the entire sync.

Webhook `processWebhookEvent(payload)` switches on `event_type` and triggers a scoped upsert for the affected `employee_id` (last 7 days lookback for leaves, last 1 day for WFH).

#### JiraSyncService.mapIssueType (rule change)
```
mapIssueType(jiraType):
  t = jiraType.toLowerCase().strip()
  if t in {"production bug","prod bug","production defect"}: return "PROD_BUG"
  if t in {"bug","uat bug","defect","uat defect"}:           return "UAT_BUG"
  return "CR"
```

**Locked in by** `JiraSyncServiceMapIssueTypeTest` (7 tests in `src/test/.../service/sync/`).

### 13.4 BugController filter wiring (correctness fix)

**Bug** in ≤ v9.0: derived JPA method `countByClientIdAndIssueTypeAndSeverityIn(0, ...)` was used when no client was selected (passing `0` as clientId), which filters `WHERE client_id = 0` — no client has id=0, so all summary tiles always returned 0.

**Fix**: 3 new `@Query` methods support `:clientId IS NULL OR ...`:
```java
@Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.issueType=:type " +
       "AND (:clientId IS NULL OR j.client.id=:clientId) " +
       "AND j.severity IN :severities " +
       "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN " +
       "    ('Closed','Invalid','Resolved','Canceled'))")
long countOpenByClientTypeAndSeverityIn(Long clientId, String type, List<String> severities);
```

`countOpenReopenedByClientAndType` and `countOpenUnassignedByClientAndType` follow the same pattern. The summary response now also carries `reopened` and `unassigned` keys that the frontend always expected but were never sent.

### 13.5 Frontend deep-link conventions

| Page | Supported URL params |
|------|----------------------|
| `/bugs` | `?severity=P0` · `?clientId=42` |
| `/cr` | `?clientId=42` · `?portfolioId=7` |

Both pages read params on mount via `useLocation()`.search and set local state; subsequent user edits don't push back to the URL (avoid history pollution).


---

## 14. v10.1 Additions — Account Detail

### 14.1 New schema (V69)

```sql
ALTER TABLE projects ADD COLUMN IF NOT EXISTS ops_model VARCHAR(20);
-- launch | bau | launch+bau

CREATE TABLE project_team (
  project_id        BIGINT PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
  internal_pm       VARCHAR(150),  internal_am       VARCHAR(150),
  internal_em       VARCHAR(150),  internal_sol      VARCHAR(150),
  client_sponsor    VARCHAR(150),  client_tech_spoc  VARCHAR(150),
  client_biz_spoc   VARCHAR(150),  client_pm         VARCHAR(150),
  updated_at        TIMESTAMP DEFAULT NOW(),
  updated_by        VARCHAR(150)
);

CREATE TABLE project_risks (
  id           BIGSERIAL PRIMARY KEY,
  project_id   BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  jira_ticket  VARCHAR(50),
  risk         TEXT   NOT NULL,
  received_on  DATE,
  rag          VARCHAR(10),                 -- Green | Amber | Red
  action_end   DATE,
  action_owner VARCHAR(150),
  source       VARCHAR(50),                 -- Client email · Status call · Escalation
  created_at   TIMESTAMP DEFAULT NOW(),
  created_by   VARCHAR(150)
);

CREATE TABLE project_releases (
  id            BIGSERIAL PRIMARY KEY,
  project_id    BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  release_date  DATE NOT NULL,
  release_type  VARCHAR(20),                -- launch | bau | support
  label         VARCHAR(150),
  rag           VARCHAR(10)
);
```

Cascade-delete on `project_id` means deleting a project cleans up all associated team/risk/release rows automatically.

### 14.2 API contract — `GET /api/v1/accounts/{projectId}`

Single aggregator response (back-end builds this in one call to avoid frontend chatter):

```json
{
  "id": 12, "name": "Atlas Launch", "opsModel": "launch+bau", "goLiveDate": "2026-04-15",
  "client":    { "id": 4, "name": "Atlas Bank",  "code": "ATLAS" },
  "portfolio": { "id": 3, "name": "Collections" },
  "stage":     "HYPERCARE",
  "healthPct": 76,
  "rag":       "Amber",

  "mandays":      { "purchased": 320, "consumed": 218, "remaining": 102, "consumedPct": 68, "status": "amber" },
  "milestones":   [{ "name": "Req Sign-off", "state": "done" }, …],
  "weeklyStatus": { "completionPct": 76, "bullets": ["…", "…"] },

  "launchOps": { "startDate": "2025-10-15", "endDate": "2026-04-15",
                 "progressPct": 64, "backlog": 18, "inProgress": 12, "closed": 56 },
  "bauOps":    { "lastUatSignOff": "2026-06-10", "lastGoLive": "2026-04-15",
                 "progressPct": 81, "backlog": 9,  "inProgress": 6,  "closed": 142 },

  "productionIssues": {
    "totalOpen": 12, "s1": 1, "s2": 4, "s3": 5, "s4": 2, "avgAgeing": 47, "closed": 38,
    "statusBreakdown": { "Blocked": 1, "Backlog": 3, … },
    "ageingBuckets":   { "0-30": 5, "31-90": 4, "91-180": 2, "180+": 1 },
    "rows":            [{ "key": "ATLAS-101", "summary": "…", "severity": "P1",
                          "status": "…", "ageing": 12, "owner": "…", "eta": null }]
  },

  "launchStories":  [{ "key": "…", "summary": "…", "status": "…", "owner": "…", "severity": null, "ageing": 0, "targetDate": null }],
  "openCrs":        [ … ],
  "productionBugs": [ … ],
  "uatBugs":        [ … ],

  "internalTeam": { "projectManager": "…", "accountManager": "…", "engineeringManager": "…", "solutionsManager": "…" },
  "clientTeam":   { "executiveSponsor": "…", "techSpoc": "…", "businessSpoc": "…", "projectManager": "…" },

  "health": {
    "sentiment":         { "score": 7.6, "label": "Positive",
                           "reasons": ["P0 production bugs contributing 8 pts", …] },
    "deliverySpeed":     { "uatItems": 42, "signedOff": 31 },
    "platformStability": { "bugsReported": 60, "bugsClosed": 38 }
  },

  "riskRegister":    [{ "id": 1, "jiraTicket": "…", "risk": "…", "receivedOn": "…",
                        "rag": "Amber", "actionEnd": "…", "actionOwner": "…", "source": "…" }],
  "releaseCalendar": [{ "id": 7, "date": "2026-06-18", "type": "launch", "label": "v2.3", "rag": "Amber" }]
}
```

### 14.3 Write endpoints

```
PUT  /api/v1/accounts/{id}/team           # PM/ADMIN; upserts ProjectTeam row
POST /api/v1/accounts/{id}/risks          # PM/ADMIN/CSM; returns { id }
DELETE /api/v1/accounts/{id}/risks/{rid}  # PM/ADMIN/CSM; cross-project guard (silent no-op if rid belongs to different project)
PUT  /api/v1/accounts/{id}/ops-model      # PM/ADMIN; body { opsModel: "launch"|"bau"|"launch+bau" }; 400 if invalid
```

### 14.4 `AccountDetailService.assemble(projectId)` algorithm

```
project = projects.findById(projectId)        // return Optional.empty if missing
hr      = health.compute(project)             // healthPct, stage, signals[]
rag     = healthPct < 50 ? "Red" : healthPct < 75 ? "Amber" : "Green"

mandays = {
  purchased = budget.purchasedDays
  consumed  = latest snapshot.burnedDays
  remaining = max(0, purchased - consumed)
  pct       = round(consumed * 100 / purchased)
  status    = pct ≥ 90 ? "red" : pct ≥ 65 ? "amber" : "green"
}

milestones = derive 6-phase array from stage
  PRE_LAUNCH   → ["done","done","current","pending","pending","pending"]
  HYPERCARE    → ["done"×6]
  STEADY_STATE → ["done"×6]

weeklyStatus.bullets = [
  "{N} open CRs",
  if openProdBugs > 0: "{N} open production bugs",
  if openUatBugs  > 0: "{N} open UAT bugs",
  "Stage: {humanised}"
]

launchOps  = countWorkflowStats(project, "CR")
bauOps     = countWorkflowStats(project, "PROD_BUG") with last UAT sign-off date
   backlog    = stages containing 'backlog', 'awaited', 'to do', 'hold'
   inProgress = stages containing 'in progress', 'in dev', 'review'
   closed     = lifecycleStage IN ('Closed', 'Released')
   progressPct= closed / total × 100

productionIssues.rows  = open PROD_BUG, top 20 by updatedAt desc
productionIssues.s1-s4 = severity counts (P0, P1, P2, P3)
productionIssues.statusBreakdown = group by lifecycleStage
productionIssues.ageingBuckets   = group by (now - createdAt) into 0-30, 31-90, 91-180, 180+

health.sentiment.reasons = top 3 ProjectHealthService signals by deduction
                            humanised: "P0 production bugs contributing 12 pts"
health.sentiment.score   = healthPct / 10 (1-decimal)
health.deliverySpeed     = { uatItems: open + closed UAT, signedOff: closed UAT }
health.platformStability = { bugsReported: open + closed PROD_BUG, bugsClosed: closed }

riskRegister     = risks.findByProjectIdOrderByCreatedAtDesc(projectId)
releaseCalendar  = releases.findByProjectIdAndReleaseDateBetween(today-14d, today+2mo)
```

### 14.5 Frontend page structure

```
/accounts/:projectId          AccountDetailPage
   useQuery(['account-detail', projectId])  → GET /accounts/{id}
   useState  tab: 'overview' | 'workbench'
   useState  workTab: 'team' | 'health'
   useState  riskOpen, riskForm

   Sticky header
      ← Back to Orbitter
      Title row: name · stage badge · RAG badge · Health %
      Subtitle: client · POD · go-live
      Actions: ops-model <select> · Export PDF button (window.print)
      Tab strip: Overview / Account Workbench       (no-print)

   Overview
      <SectionCard title="Weekly status report">
         6 milestone cards + bullets
      <SectionCard title="Mandays">
         3 ManCard (Total · Consumed [progress bar] · Remaining)
      <SectionCard title="Project timeline & delivery health">
         OpsBlock (launch) | OpsBlock (bau)
      <SectionCard title="Risk register">
         [+ Add risk] button toggles inline form
         Table of risks with delete-per-row
      <SectionCard title="Production issues tracker">
         6 Stat cards + 2 Breakdown panels + IssueTable
      <SectionCard title="Release calendar">
         Cards coloured by type (launch/bau/support)
      Grid of 3:
         WorklistColumn × 3 (launch stories · open CRs · prod bugs)

   Workbench
      Sub-tab strip: Team | Health
      Team
         SectionCard "Internal team"  → 4 ContactCard
         SectionCard "Client team"    → 4 ContactCard
      Health
         SectionCard "Sentiment & delivery" → 3 HealthCard
         SectionCard "Open UAT bugs"        → IssueTable

   <style>@media print { .no-print { display: none !important } body { background: white } }</style>
```

### 14.6 Routing change

`Shell.tsx` adds the route:

```tsx
import { AccountDetailPage } from '../features/accounts/AccountDetailPage'
…
<Route path="/accounts/:projectId" element={<AccountDetailPage />} />
```

`RadarPage` account cards (under Account Details section):

```tsx
<div onClick={() => navigate(`/accounts/${acct.id}`)} … >
```

Previously navigated to `/cr?clientId={acct.clientId}` — that deep-link is now superseded by the richer per-project detail view. CR board is still independently navigable via the sidebar.

## 15. Slack bidirectional agent (Phases 1–5)

End-to-end inbound flow for `/orbit`, `@orbit`, and DMs. This section is the canonical wiring reference.

### 15.1 Inbound dispatch

```
Slack → POST /api/v1/slack/{events|commands|interactions}
  SlackEventController.verify()         // HMAC-SHA256 against slack_config.signing_secret
    ↓
  SlackInteractionRouter.dispatch{Event|SlashCommand|Interaction}
    ↓ load thread context
  SlackConversationStore.load(threadKey)  → Optional<SlackTurnContext>
    ↓
  IntentResolver.resolve(text, priorArgs)
    ↓ parseSlash() → ResolvedIntent | LLM fallback (Haiku) | empty
  SlackToolExecutor.execute(intent, user, surface)
    ↓ branches on intent.tool()
  SlackResponseRenderer.<card>(rows)  +  withSuggestions(blocks, suggestionsFor(intent))
    ↓
  send(channel, threadTs, slackUserId, fallback, blocks, surface)
    ↓ Surface=MENTION  → slack.postInThread
       Surface=DM      → slack.postMessage
       Surface=SLASH   → slack.postEphemeral
    ↓ save next turn
  SlackConversationStore.save(threadKey, SlackTurnContext)
```

Self-reply guard (Phase 5.4): events with `bot_id != null` OR `subtype == "bot_message"` OR `user == null` are dropped before resolve — prevents the bot's own DM (e.g. the magic-link DM) from re-entering the pipeline.

### 15.2 Read-only tool catalog (SlackToolExecutor)

| Intent tool | Source (repo / service · method) | Card builder | Project filter |
|---|---|---|---|
| `orbit.get_alerts` | `AlertRepository.findFiltered(sevUpper, "OPEN", null, PageRequest.of(0,10))` | `renderer.alerts(rows)` | n/a |
| `orbit.get_bugs` | `JiraIssueRepository.findProdBugs(null, sev, null, PageRequest.of(0,50))` | `renderer.bugSummary(p0..p3, slaBreached)` | n/a |
| `orbit.get_crs` | `JiraIssueRepository.findCrs(null,null,null,p10)` OR `findCrsByProjectIds(ids,...)` when `projectName` resolves | `renderer.crs(rows)` | `findProjectIdsByName(name)` over `findByActiveTrue()` (case-insensitive) |
| `orbit.get_briefing` | counts via `alerts.findFiltered("CRITICAL"/"WARNING",...)`, `jira.findProdBugs(null,"P0",...)`, `jira.findCrs(...)` (page size 1 → `getTotalElements()`) | `renderer.briefing(lines)` — severity dots `critical/watch/info/healthy` | n/a (global) |
| `orbit.get_forecast` | latest `ManDaySnapshotRepository.findTop14ByProjectIdOrderBySnapshotDateDesc(projectId)` | `renderer.forecast(projectName, burnPct, rate, exhaustionDate, daysToExhaust)` | required `projectName` arg; empty-state when no snapshot |
| `orbit.get_capacity` | `DeveloperRepository.findAllByOrderByUtilizationDesc()` grouped by `team` in-memory; avg utilisation per team | `renderer.capacity(rows)` — RAG dot >85 red / >70 amber / else green | n/a |
| `orbit.get_report_status` | `GeneratedReportRepository.findFiltered(null, PageRequest.of(0,5))` | `renderer.reportStatus(rows)` | n/a |

`burnPct = burned / (burned + remaining) * 100` (rounded half-up). `daysToExhaust = remaining / burnRatePerDay` when rate > 0. All exhaustion dates serialise as `ISO_LOCAL_DATE`. All cards end with a `withSuggestions(blocks, suggestionsFor(intent))` actions block (`action_id="next:<tool>"`, `value=<argsJson>`) — clicked button re-enters `dispatch` with merged thread context.

### 15.3 Invocation tools

| Intent | Native agent key | Source |
|---|---|---|
| `orbit.run_report` | `report.draft` (requires `reportId` arg) | `SLACK_SLASH` / `SLACK_MENTION` / `SLACK_DM` |
| `orbit.run_forecast` | `forecast.manday` | same |
| `orbit.run_briefing` | `briefing.delivery` | same |

`AgentInvocationService.invoke(user, agentKey, args, source)` enforces `SLACK_INVOKE_ROLES = {ADMIN, PJM}` for `SLACK_*` sources. HITL-gated steps inside the run trigger `HitlAwaitingEvent`; `SlackHitlBridge` posts an approval card to the default channel; `SlackInteractionRouter.handleBlockAction` routes `hitl:{approve|reject|edit}:<runId>:<stepId>` through `HitlApprovalService`.

### 15.4 Account binding (`/orbit-link`)

```
/orbit-link <email>
  SlackLinkCommandHandler.handle(slackUserId, email)
    ↓ AppUserRepository.findByEmail → present?
       no  → DM vague "if it's an Orbit account, a link has been sent" (anti-enumeration)
       yes → MagicLinkService.issue(slackUserId, email) → 15-min TTL
            slack.sendDm(slackUserId, "Click to link… {frontendUrl}/slack/link?token=…")
  ↓ user clicks
React /slack/link?token=…   (SlackLinkConfirmPage, public route — token IS the auth)
  POST /api/v1/slack/link/confirm  { token }
    SlackEventController.confirmLink → MagicLinkService.consume(token)
       → app_users.slack_user_id = magicLink.slackUserId
       → slack.sendDm(slackUserId, ":white_check_mark: Linked as `<email>`…")
```

### 15.5 Conversational memory (Phase 4)

`SlackConversationStore` persists per-thread `SlackTurnContext(lastTool, projectName, severity, lastRunId)` JSON in `agent_memory`:
- `memory_type='SLACK_THREAD'`, `mem_key='slack_thread:<thread_ts|channel>'`, `agent_id=NULL`
- `expires_at = now + 60min` — `load()` defensively skips expired rows even if pruning lags
- Repo method: `AgentMemoryRepository.findByMemoryTypeAndMemKey(type, key)` (ordered by `createdAt DESC`)

`IntentResolver.resolve(userText, priorArgs)` merges `priorArgs` under the new turn's args (current turn wins on conflict). So `@orbit alerts` after `@orbit bugs apollo` in the same thread keeps `projectName=Apollo`.

### 15.6 Configuration & secrets

- `slack_config` row (admin-managed via `IntegrationsController` PUT `/admin/integrations/slack`):
  - `bot_token` — AES-256-GCM via `SlackEncryptionService` (key from env `SLACK_TOKEN_ENC_KEY`, base64 of 32 bytes). Without the env var a fresh key is generated each restart and tokens become unreadable — log emits a SECURITY warning at startup.
  - `signing_secret` — plain text (used by `SlackSignatureVerifier` directly; not encrypted).
  - `default_channel`, `workspace_name`, `enabled`.
- Save-path guards (both fields): skip `null`, blank, or values starting with `***` (masked placeholder) so an admin updating one field doesn't clobber the other.
- Per-project channel mapping: `slack_project_channels`. `SlackService.resolveChannel(projectId)` checks project mapping → falls back to `default_channel`.

### 15.7 Tests

| Test class | Count | Scope |
|---|---|---|
| `SlackToolExecutorTest` | 22 | Every read-only branch (no-project / project-scoped / unknown-project / empty) + every invocation source + suggestion appending |
| `IntentResolverTest` | 19 | Deterministic parser, LLM fallback, context merge |
| `SlackInteractionRouterTest` | ≥15 | Dispatch surfaces, HITL routing, next-action button, unlinked user safety |
| `SlackConversationStoreTest` | 5 | Thread-key derivation, save, latest-non-expired, all-expired, inheritable-args projection |
| `SlackResponseRendererTest` | snapshots | Block Kit shapes for each card |
| `SlackSignatureVerifierTest` | — | HMAC + replay-window |
| `MagicLinkServiceTest` | 5 | issue + consume + expiry |
| `SlackLinkCommandHandlerTest` | 4 | known-email / unknown-email / bad-email / DM path |
| `SlackEvalRunnerTest` | 20 | YAML-driven utterance regression (`docs/agent-evals/slack-status-queries.yaml`) |
| `SnapshotSlackHandlerTest` | 6 | `/orbit snapshot` text match, modal open (any role — JWT elevation handles permissions), view_submission → SnapshotService + DM, lens fallback, cached-result headline |
| `JwtFilterSnapshotScopeTest` | 4 | Snapshot-scoped JWT elevates to `ROLE_ADMIN` on GET only; non-snapshot scopes / mutating verbs never elevate |

Full backend gate: `mvn clean test` 446/446 as of 2026-06-28 (Slack Phase 5 + Snapshot Reporting Agent shipped end-to-end).

### 15.8 Snapshot Reporting Agent dispatch *(new — 2026-06-28)*

```
/orbit snapshot [free text ignored]
  SlackInteractionRouter.dispatchSlashCommand:
    snapshots.matchesSlashText("snapshot")  → true
       identity.resolveOrbitUser(slackUserId)
       SnapshotSlackHandler.openModal(triggerId, user)
         → slack.openView({ callback_id: "orbit_snapshot",
                           blocks: [portfolio_block, lens_block, project_block] })
       (lens defaults to user.role; portfolios sourced from PortfolioRepository.findByActiveTrue())

view_submission (callback_id = "orbit_snapshot"):
  SlackInteractionRouter.handleViewSubmission → snapshots.isOurSubmission → handleSubmission:
    1. extract portfolioId, lens (fallback user.role), projectId
    2. SnapshotService.request(user, SnapshotArgs)
         → SnapshotResult{ id, state, fromCache, dedup }
    3. DM the slackUserId with /snapshots/{id} link
       headline = fromCache  → ":white_check_mark: Reusing a recent snapshot — opens instantly."
                  dedup      → ":hourglass_flowing_sand: That snapshot is already in progress."
                  otherwise  → ":camera: Snapshot queued. The link below tracks progress."
```

### 15.9 `SnapshotService.request` *(new — 2026-06-28)*

```java
SnapshotResult request(AppUser user, SnapshotArgs args) {
    requireNonNullUser(user);
    String key = fingerprint(userId, args);   // SHA-256 → 16 hex chars
    if (snapshots.findReadySince(key, now - cacheTtl).nonEmpty)
        return cached(latest.id);             // skip render entirely

    Snapshot row = new Snapshot(PENDING, key, args, expires=now+7d);
    try {
        Snapshot saved = snapshots.saveAndFlush(row);    // racing inserts collide here
        agent.renderAsync(saved.id);                     // @Async — never blocks the caller
        return fresh(saved.id, PENDING);
    } catch (DataIntegrityViolationException e) {        // hit on uq_snapshot_inflight
        return dedupHit(snapshots.findInflight(key).first.id, state);
    }
}

// RadarSnapshotAgent.renderAsync(snapshotId)   [@Async]
//   1. load row + user; flip to RUNNING; persist
//   2. mint short-lived JWT (5-min, scope=snapshot:read)
//   3. build targetUrl = "{frontend}/radar?snapshot=1&lens=…&portfolio=…&project=…"
//   4. SnapshotRendererClient.render(targetUrl, jwt, viewport, readySelector, timeout)
//   5. SnapshotStorageService.save(id, png, pdf) → {pngPath, pdfPath}
//   6. flip row to READY (or FAILED + errorMessage) and set completed_at
// All exceptions are caught: the @Async thread NEVER throws back to the executor.

// SnapshotWatchdog.sweep()                     [@Scheduled fixedDelayMs=30_000]
//   any PENDING/RUNNING older than stuck-cutoff-seconds (default 60s)
//   → marked FAILED with "watchdog: stuck for > Xs" so uq_snapshot_inflight frees.
```

Renderer client is pluggable on `snapshot.renderer`:
- `mock` (default) → `MockSnapshotRendererClient` returns a 1×1 PNG + minimal PDF (used by tests and local-dev without the sidecar).
- `http` → `HttpSnapshotRendererClient` POSTs to `snapshot.sidecar.url/render` and base64-decodes the response. Production / Docker compose.

### 15.10 Snapshot JWT scope elevation *(security-relevant)*

The snapshot JWT carries `scope=snapshot:read` alongside the requester's normal `userId`, `email`, `role` claims. When `JwtFilter` sees this scope it adds `ROLE_ADMIN` to the request authorities — **but only when `req.getMethod()` is GET**. This is what lets an Engineering user pick the Leadership lens and actually load `/dashboard/radar` (which requires `hasAnyRole('PM','LEADERSHIP','ADMIN')`).

Mutating verbs (POST, PUT, DELETE, PATCH) skip the elevation, so the snapshot JWT can never:
- write to any controller protected by `hasAnyRole(...)` on a mutating endpoint,
- invoke an agent (all `AgentInvocationService` paths are POST),
- approve/reject a HITL step (POST on `/admin/agents/runs/{runId}/steps/{stepId}/{approve|reject}`),
- modify man-day budgets (POST/PUT on `ManDayController`).

Combined with:
- 5-min TTL on the token,
- URL-only delivery path (sidecar appends `?token=…`; frontend `api/client.ts` only reads `?token=` when `?snapshot=1` is also set),
- the token's `sub` resolves to the requester so audit trails still attribute the read correctly,

the blast radius of a leaked snapshot JWT is bounded to "read-only access for ≤5 min to the data the requester would have seen with admin permissions". Pinned by `JwtFilterSnapshotScopeTest` (4 tests covering GET elevation, POST non-elevation, missing-scope non-elevation, wrong-scope non-elevation).

### 15.11 Frontend snapshot mode

Headless Chromium boots with a fresh localStorage — there is no zustand `user`, so the normal `RequireAuth` guard in `App.tsx` would redirect to `/login` and the radar route would never mount. Snapshot mode bypasses this:

```tsx
function RequireAuth({ children }) {
  const user = useStore(s => s.user)
  if (!user && !readSnapshotParams().enabled) return <Navigate to="/login" replace />
  return children
}
```

`readSnapshotParams()` is the single source of truth for snapshot mode (lives in `frontend/src/app/snapshotMode.ts`). It returns `{ enabled, portfolioId, lens, projectId }` parsed from `?snapshot=1&portfolio=…&lens=…&project=…`.

The same flag drives chrome-hiding in `Shell.tsx` (TopBar + GlobalCopilotPanel are not rendered) and the URL-token override in `api/client.ts`:

```ts
function snapshotToken() {
  const sp = new URLSearchParams(window.location.search)
  if (sp.get('snapshot') !== '1') return null  // refuse to read ?token= outside snapshot mode
  return sp.get('token')
}
api.interceptors.request.use((config) => {
  const token = snapshotToken() ?? useStore.getState().user?.token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
```

The `?snapshot=1` gate on `snapshotToken()` is deliberate: a stray `?token=` in any non-snapshot URL is ignored.

The page signals readiness to the sidecar by setting `data-snapshot-ready="true"` on the outermost `<div>` of `RadarPage.tsx` once `activePid`, `portfolioSummary`, and `radarData` are all resolved. The sidecar polls for that selector before capturing.

