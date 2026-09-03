# Orbit

[![CI](https://github.com/Akshath0392/orbit/actions/workflows/ci.yml/badge.svg)](https://github.com/Akshath0392/orbit/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

AI-native delivery intelligence platform for PMs, delivery leadership, CSM, and revenue teams. Tracks Jira-driven delivery (CRs, bugs, UAT, SLAs), per-project health, capacity, and HRMS attendance (pluggable connectors; Darwinbox included) — with a Copilot and 6 AI agents (HITL-gated) layered on top.

For architecture, design, and screen catalogue see [`docs/design/hld.md`](docs/design/hld.md) and [`docs/design/lld.md`](docs/design/lld.md). For Claude-session loading rules see [`docs/CLAUDE.md`](docs/CLAUDE.md).

## Prerequisites

- **Java 25** (`sdk install java 25.0.2-open`)
- **Node 20+**
- **PostgreSQL 16 + pgvector** (or use the docker-compose Postgres)
- **Anthropic or OpenAI API key** (set in `.env`; copilot falls back to a scripted demo response if unset)

## Quickstart — Docker

```bash
cp .env.example .env                 # fill in ANTHROPIC_API_KEY or OPENAI_API_KEY
docker-compose up --build
```

- Frontend: <http://localhost:3000>
- Backend API: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

## Quickstart — Local

```bash
docker-compose up postgres redis -d  # infra only
./scripts/setup-db.sh                # first time: creates orbit user + DB
./scripts/start-all.sh --rebuild     # build + start backend + frontend
```

Logs land in `/tmp/orbit-backend.log` and `/tmp/orbit-frontend.log`.

Fresh installs start with an empty database (no clients, portfolios or projects). For screenshots or a first-run evaluation, load the neutral demo dataset — either set `SEED_DEMO_DATA=true` in `.env` before running `./scripts/start-all.sh`, or apply it manually once the backend has booted (Flyway must have created the schema):

```bash
psql -U orbit -d orbit -f scripts/seed-demo.sql
```

The seed is idempotent and fictional (Acme Corp / Globex / Initech); re-running it is a no-op.

On a freshly seeded DB a bootstrap `admin@orbit.io` account is created. Its password comes from `ORBIT_ADMIN_PASSWORD`; if that is unset, a random password is generated and printed once in the backend startup log — log in with it and change it immediately. Google SSO is enabled when `GOOGLE_CLIENT_ID` + `GOOGLE_CLIENT_SECRET` are present.

## Connect your Jira

Orbit syncs from Jira Cloud (REST v3) using an [Atlassian API token](https://id.atlassian.com/manage-profile/security/api-tokens):

1. Log in as an admin → **Admin → Integrations → Jira**. Enter your site base URL (`https://your-site.atlassian.net`), the email of the syncing account, and its API token.
2. **Admin → Clients / Projects**: create a client and a project, and set the project's Jira project key(s) (a JQL override is available for advanced scoping).
3. **Field mapping** (same Jira card): map your custom fields — SLA, story points, sprint, developer, etc. Unmapped fields are simply skipped.
4. **Admin → Lifecycle mapping**: map your Jira statuses to delivery stages per issue type (CR / PROD_BUG / UAT_BUG / TASK). A stage catalog seeds sensible defaults and auto-discovers unmapped statuses during sync.
5. Hit **Full sync**. Progress, project scope, and run history are visible live on the same page; after the first full sync a delta sync runs every 30 minutes automatically.

Dashboards (radar, account detail, CR workbench, bug triage) populate as soon as issues land.

## Project map

```
backend/                       Spring Boot 3.2 / Java 25
  src/main/java/com/orbit/
    controller/                REST endpoints (admin + app)
    service/                   business logic + AI agents
      agent/                   AgentRuntime, NativeAgentRegistry, HITL
      slack/                   Slack identity / intent / tools
    integration/slack/         Slack signature + interaction router
    domain/                    JPA entities
    repository/                Spring Data repos
    security/                  JWT, RBAC, signature verifiers
  src/main/resources/db/migration/   Flyway V16–V100
frontend/                      React 18 + TypeScript + Vite
  src/features/<screen>/       one folder per screen (kebab-case)
  src/design/                  shared atoms (custom DS — no MUI/Tailwind)
docs/                          HLD, LLD, CLAUDE, agent-evals, plans
scripts/                       setup-db, start-backend, start-frontend, start-all
```

## Common dev tasks

```bash
# Backend tests (~390)
cd backend && mvn test

# Frontend tests
cd frontend && npx vitest run

# Run a single backend test
cd backend && mvn test -Dtest=SlackInteractionRouterTest

# Fresh build after pulling
./scripts/start-all.sh --rebuild

# Run migrations only
cd backend && mvn flyway:migrate

# Slack dev (expose local backend)
ngrok http 8080      # paste the https URL into api.slack.com/apps
```

## Script flags

| Flag | Effect |
|------|--------|
| _(none)_ | Start using existing JAR; build only if JAR is missing |
| `--rebuild` / `--build` | Force `mvn clean package` before starting |
| `--test` | Run backend + frontend tests before starting |

```bash
./scripts/start-backend.sh --rebuild
./scripts/start-frontend.sh
./scripts/start-all.sh --rebuild --test
```

> Always pass `--rebuild` after pulling or editing backend source — the running process serves the compiled JAR.

## Environment variables

| Variable | Purpose |
|---|---|
| `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` | Copilot LLM |
| `AI_PROVIDER` | `anthropic` (default) or `openai` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google SSO |
| `GOOGLE_REDIRECT_URI` | Override default callback (`http://localhost:8080/api/v1/auth/google/callback`) |
| `FRONTEND_URL` | Redirect after SSO callback (default `http://localhost:3000`) |
| `ORBIT_JIRA_BASE_URL`, `ORBIT_JIRA_EMAIL`, `ORBIT_JIRA_API_TOKEN` | Jira credentials env fallback (DB config takes precedence) |
| `JWT_SECRET`, `ORBIT_JWT_PRIVATE_KEY`, `ORBIT_JWT_PUBLIC_KEY` | JWT signing |
| `ORBIT_CORS_ALLOWED_ORIGINS` | Comma-separated CORS origins |
| `INTERNAL_EMAIL_DOMAINS` | Comma-separated company email domains (blank = no restriction; drives Slack link validation + help-text examples) |
| `SEED_DEMO_DATA` | `true` = `start-all.sh` loads `scripts/seed-demo.sql` after boot (default off) |

Slack bot token + signing secret are configured in the admin console (Admin → Integrations → Slack), not in env vars.

## Where to read more

- [`docs/design/hld.md`](docs/design/hld.md) — architecture, modules, screen catalogue, RBAC matrix, sprint plan
- [`docs/design/lld.md`](docs/design/lld.md) — full schema DDL, REST contracts, WS events, service algorithms, config
- [`docs/CLAUDE.md`](docs/CLAUDE.md) — Claude-session loading skill (locked conventions, hard don'ts)
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — what's shipped and what's under consideration
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — dev setup, guidelines, PR checklist

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
