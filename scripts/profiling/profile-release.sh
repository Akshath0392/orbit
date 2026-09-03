#!/usr/bin/env bash
# Per-release performance report — run after every deploy (release procedure step 6).
#   Usage: scripts/profiling/profile-release.sh <version-label>   (e.g. v1.2.0)
# Writes perf-reports/<version>_<yyyy-mm-dd>/ with:
#   meta.txt · api-benchmark.csv · db-stats.txt · containers.txt · gc-summary.txt · actuator.txt
# Compare two releases with compare-reports.sh.
# See docs/plan/release-performance-profiling-plan.md
set -euo pipefail

VERSION="${1:?usage: profile-release.sh <version-label>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
BASE_URL="${ORBIT_BASE_URL:-http://localhost:8080}"
OUT="$ROOT/perf-reports/${VERSION}_$(date +%F)"
mkdir -p "$OUT"

echo "== Orbit release profile: $VERSION → $OUT"

{
  echo "version:   $VERSION"
  echo "date:      $(date -u +%FT%TZ)"
  echo "host:      $(uname -a)"
  echo "git_sha:   $(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo n/a)"
  echo "git_desc:  $(git -C "$ROOT" describe --tags --always 2>/dev/null || echo n/a)"
  echo "base_url:  $BASE_URL"
  echo "bench_n:   ${ORBIT_BENCH_N:-30} × concurrency ${ORBIT_BENCH_CONCURRENCY:-1}"
  docker images --format 'image:     {{.Repository}}:{{.Tag}} ({{.ID}})' 2>/dev/null | grep -i orbit || true
} > "$OUT/meta.txt"

echo "-- API benchmark (this is the slow part)…"
"$HERE/api-benchmark.sh" > "$OUT/api-benchmark.csv"
column -s, -t < "$OUT/api-benchmark.csv"

echo "-- DB stats…"
"$HERE/db-stats.sh" > "$OUT/db-stats.txt" 2>&1 || echo "   (db-stats failed — see file)"

echo "-- Container resources…"
docker stats --no-stream 2>/dev/null > "$OUT/containers.txt" || echo "docker unavailable" > "$OUT/containers.txt"

echo "-- GC log summary…"
GC_LOG="${ORBIT_GC_LOG:-/var/log/orbit/gc.log}"
if [ -r "$GC_LOG" ]; then
  { echo "source: $GC_LOG (last 200 lines)"; tail -200 "$GC_LOG"; } > "$OUT/gc-summary.txt"
elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^orbit-backend$'; then
  docker exec orbit-backend sh -c 'tail -200 /var/log/orbit/gc.log 2>/dev/null' > "$OUT/gc-summary.txt" \
    || echo "GC log not enabled — add JAVA_OPTS per infra plan §5" > "$OUT/gc-summary.txt"
else
  echo "GC log not found" > "$OUT/gc-summary.txt"
fi

# Actuator is optional (deferred to Phase 1) — include metrics when present.
if curl -sf --max-time 3 "$BASE_URL/actuator/health" > /dev/null 2>&1; then
  { curl -s "$BASE_URL/actuator/health"; echo
    for m in jvm.memory.used jvm.gc.pause hikaricp.connections.active http.server.requests; do
      echo "--- $m"; curl -s "$BASE_URL/actuator/metrics/$m"; echo
    done
  } > "$OUT/actuator.txt"
else
  echo "actuator not enabled (expected during pilot — see profiling plan, Future section)" > "$OUT/actuator.txt"
fi

echo "== Done: $OUT"
echo "   Next: scripts/profiling/compare-reports.sh <previous-report-dir> $OUT"
