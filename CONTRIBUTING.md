# Contributing to Orbit

Thanks for your interest in improving Orbit! This document covers the basics of
getting a change from idea to merged PR.

## Getting set up

Follow the Quickstart in [README.md](README.md). The short version:

```bash
docker-compose up postgres redis -d
./scripts/setup-db.sh
./scripts/start-all.sh --rebuild
```

Set `SEED_DEMO_DATA=true` in `.env` for a neutral demo dataset (Acme / Globex /
Initech) so dashboards aren't empty.

## Development guidelines

- **Backend** is Spring Boot 3.2 targeting Java 21. Schema changes go through
  Flyway (`backend/src/main/resources/db/migration/`) — never edit an applied
  migration; add a new `V<next>__description.sql`.
- **Frontend** is React 18 + TypeScript + Vite with a custom design system —
  no Tailwind, no MUI. Reuse the composites in `frontend/src/design/components/`
  (PageHeader, StatGrid, FilterBar, TableWrap, StatusPill, …) before writing
  new UI.
- All frontend date rendering goes through `frontend/src/lib/datetime.ts`.
- Any new mutation endpoint whose data feeds a cached dashboard payload must
  carry `@EvictsDashboardCaches` (see `docs/design/lld.md` §6.13).
- Match the style of surrounding code; keep comments for the *why*, not the what.

## Before opening a PR

```bash
# Backend
cd backend && mvn test

# Frontend
cd frontend && npx tsc --noEmit && npx vitest run
```

All of these run in CI on every PR; green locally means green in CI.

- Keep PRs focused — one feature or fix per PR.
- Include a test for behavior changes (unit test for services, MockMvc test for
  controllers, vitest for frontend logic).
- Update `docs/design/hld.md` / `docs/design/lld.md` when you change
  architecture, schema, or REST contracts.

## Reporting bugs / requesting features

Use the issue templates. For bugs, include reproduction steps, expected vs.
actual behavior, and relevant log output (`/tmp/orbit-backend.log`).

## Security issues

Please do **not** open a public issue for security vulnerabilities — email the
maintainer instead (see the repository profile).

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE).
