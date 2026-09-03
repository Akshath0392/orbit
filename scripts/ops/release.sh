#!/usr/bin/env bash
# Orbit release driver — runs the full release procedure from
# docs/plan/production-pilot-infra-deployment-plan.md §5 ("every release, no exceptions"):
#   1 test gate (backend mvn test · frontend tsc + vitest) + git tag
#   2 GIT_SHA-tagged docker compose build (prod overlay)
#   3 pre-deploy DB backup (scripts/ops/backup-db.sh)
#   4 compose up -d — Flyway migrates on backend boot
#   5 smoke: login · /dashboard/radar · /feature-flags/effective (+ manual Slack test)
#   6 profile: scripts/profiling/profile-release.sh
#   7 compare vs previous report
#   8 flag-rollout reminder (NONE → PILOT → ALL)
#
#   Usage: scripts/ops/release.sh vX.Y.Z
#   Env:   ORBIT_BASE_URL (default http://localhost:8080)
#          ORBIT_SMOKE_EMAIL / ORBIT_SMOKE_PASSWORD (prompted when interactive)
#          ORBIT_SKIP_TESTS=1 — emergencies only; noted loudly
#
# Rollback (plan §5): previous image tag + the step-3 dump. This script prints both
# at the point of failure. Flyway is forward-only — never roll a migration back in place.
set -euo pipefail

VERSION="${1:?usage: release.sh vX.Y.Z}"
[[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "ERROR: version must look like v1.2.3 (got '$VERSION')" >&2; exit 1; }

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
BASE_URL="${ORBIT_BASE_URL:-http://localhost:8080}"
COMPOSE=(docker compose -f "$ROOT/docker-compose.yml" -f "$ROOT/docker-compose.prod.yml")
cd "$ROOT"

step() { echo; echo "==[$1/8]== $2"; }
die()  { echo "ERROR: $*" >&2; exit 1; }

# ---- preflight -------------------------------------------------------------
echo "== Orbit release $VERSION — preflight"
command -v docker >/dev/null || die "docker not installed"
docker compose version >/dev/null 2>&1 || die "docker compose v2 not available"
[ -f "$ROOT/docker-compose.prod.yml" ] || die "docker-compose.prod.yml missing — create the prod overlay per infra plan §5 before releasing. Refusing to deploy the dev compose file."
[ -f "$ROOT/.env" ] || die ".env missing — rotated prod secrets are required (infra plan §3). Refusing to deploy with baked-in dev defaults."

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
[ "$BRANCH" = "main" ] || die "releases cut from main only (on '$BRANCH')"
# -uno: untracked ops artifacts (backups/, perf-reports/) must not block a release;
# modified tracked files must.
[ -z "$(git status --porcelain -uno)" ] || die "working tree has uncommitted changes to tracked files — commit or stash first"
if git rev-parse -q --verify "refs/tags/$VERSION" >/dev/null; then die "tag $VERSION already exists"; fi

GIT_SHA="$(git rev-parse --short HEAD)"
export GIT_SHA
PREV_IMAGE="$(docker inspect orbit-backend --format '{{.Config.Image}}' 2>/dev/null || echo 'none (first deploy)')"
echo "   sha: $GIT_SHA · previous backend image: $PREV_IMAGE"

# ---- 1. tests + tag --------------------------------------------------------
step 1 "test gate + git tag $VERSION"
if [ "${ORBIT_SKIP_TESTS:-0}" = "1" ]; then
  echo "   !! ORBIT_SKIP_TESTS=1 — TEST GATE SKIPPED. This violates the release procedure; justify it in the release notes."
else
  mvn -f "$ROOT/backend/pom.xml" test
  ( cd "$ROOT/frontend" && npx tsc --noEmit && npx vitest run )
fi
git tag "$VERSION"
echo "   tagged $VERSION at $GIT_SHA (push later with: git push origin $VERSION)"
# From here on, a failed release should drop the tag before re-cutting: git tag -d $VERSION

# ---- 2. build --------------------------------------------------------------
step 2 "docker compose build (images pinned to $GIT_SHA)"
"${COMPOSE[@]}" build

# ---- 3. backup -------------------------------------------------------------
step 3 "pre-deploy DB backup"
"$HERE/backup-db.sh"
LAST_DUMP="$(ls -1t "$ROOT"/backups/daily/orbit_*.dump 2>/dev/null | head -1 || true)"
[ -n "$LAST_DUMP" ] && [ -s "$LAST_DUMP" ] || die "backup did not produce a dump — not deploying without one"

# ---- 4. deploy -------------------------------------------------------------
step 4 "deploy (Flyway migrates on backend boot)"
"${COMPOSE[@]}" up -d
echo -n "   waiting for backend"
UP=false
for _ in $(seq 1 60); do  # up to 5 min — migrations can be slow on big tables
  CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 -X POST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' -d '{}' 2>/dev/null || echo 000)"
  if [ "$CODE" != "000" ]; then UP=true; echo " up (http $CODE)"; break; fi
  echo -n "."; sleep 5
done
$UP || { echo; die "backend not responding after 5 min — check: ${COMPOSE[*]} logs backend | rollback: redeploy $PREV_IMAGE and restore $LAST_DUMP if a migration ran"; }

# ---- 5. smoke --------------------------------------------------------------
step 5 "smoke checks against $BASE_URL"
if [ -z "${ORBIT_SMOKE_EMAIL:-}" ] && [ -t 0 ]; then read -r -p "   smoke login email: " ORBIT_SMOKE_EMAIL; fi
if [ -z "${ORBIT_SMOKE_PASSWORD:-}" ] && [ -t 0 ]; then read -r -s -p "   smoke login password: " ORBIT_SMOKE_PASSWORD; echo; fi
[ -n "${ORBIT_SMOKE_EMAIL:-}" ] && [ -n "${ORBIT_SMOKE_PASSWORD:-}" ] || die "set ORBIT_SMOKE_EMAIL / ORBIT_SMOKE_PASSWORD for the smoke login"

smoke_fail() {
  echo "SMOKE FAILED: $1" >&2
  echo "Rollback (plan §5): redeploy previous images → GIT_SHA=<prev-sha> ${COMPOSE[*]} up -d" >&2
  echo "  previous backend image: $PREV_IMAGE" >&2
  echo "  pre-deploy dump:        $LAST_DUMP (restore only if a bad migration ran — Flyway is forward-only)" >&2
  exit 1
}

TOKEN="$(curl -s --max-time 10 -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ORBIT_SMOKE_EMAIL\",\"password\":\"$ORBIT_SMOKE_PASSWORD\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin).get("token",""))' 2>/dev/null || true)"
[ -n "$TOKEN" ] || smoke_fail "login"
echo "   ✓ login"

for EP in /api/v1/dashboard/radar /api/v1/feature-flags/effective; do
  CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -H "Authorization: Bearer $TOKEN" "$BASE_URL$EP")"
  [ "$CODE" = "200" ] || smoke_fail "GET $EP → $CODE"
  echo "   ✓ GET $EP"
done
echo "   → manual: send a Slack test (/orbit in the ops channel) and confirm the reply"

# ---- 6+7. profile + compare ------------------------------------------------
step 6 "profiling report"
"$ROOT/scripts/profiling/profile-release.sh" "$VERSION"

step 7 "compare vs previous release"
REPORT_NEW="$(ls -1dt "$ROOT"/perf-reports/*/ 2>/dev/null | sed -n 1p)"
REPORT_PREV="$(ls -1dt "$ROOT"/perf-reports/*/ 2>/dev/null | sed -n 2p)"
if [ -n "$REPORT_PREV" ]; then
  "$ROOT/scripts/profiling/compare-reports.sh" "$REPORT_PREV" "$REPORT_NEW"
else
  echo "   no previous report — this run is the baseline all future releases compare against"
fi

# ---- 8. done ---------------------------------------------------------------
step 8 "release $VERSION deployed"
echo "   · push the tag:            git push origin $VERSION"
echo "   · new features stay flagged NONE → verify as ADMIN → PILOT emails → ALL over days, not minutes (plan §4)"
echo "   · keep the last 3 image sets; prune older: docker images orbit-backend"
