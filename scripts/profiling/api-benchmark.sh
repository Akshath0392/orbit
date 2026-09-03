#!/usr/bin/env bash
# Times N requests against the key Orbit endpoints and prints a CSV:
#   endpoint,count,errors,p50_ms,p95_ms,p99_ms,max_ms
# Config via env: ORBIT_BASE_URL, ORBIT_BENCH_EMAIL, ORBIT_BENCH_PASSWORD,
#                 ORBIT_BENCH_N (requests/endpoint), ORBIT_BENCH_CONCURRENCY.
# See docs/plan/release-performance-profiling-plan.md
set -euo pipefail

BASE_URL="${ORBIT_BASE_URL:-http://localhost:8080}"
EMAIL="${ORBIT_BENCH_EMAIL:-admin@orbit.io}"
PASSWORD="${ORBIT_BENCH_PASSWORD:?set ORBIT_BENCH_PASSWORD}"
N="${ORBIT_BENCH_N:-30}"
CONCURRENCY="${ORBIT_BENCH_CONCURRENCY:-1}"

# name + path pairs; keep in sync with the endpoint set in the profiling plan doc
ENDPOINTS="
radar|/api/v1/dashboard/radar
clients|/api/v1/clients
projects|/api/v1/projects
alerts|/api/v1/alerts?page=0&size=20
man-days|/api/v1/man-days
portfolios|/api/v1/portfolios
flags|/api/v1/feature-flags/effective
roles|/api/v1/admin/roles
"

token=$(curl -s --max-time 10 -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  | sed -n 's/.*"token" *: *"\([^"]*\)".*/\1/p')
if [ -z "$token" ]; then
  echo "ERROR: login failed against $BASE_URL as $EMAIL" >&2
  exit 1
fi

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

run_one() { # $1=path $2=outfile — appends "<http_code> <seconds>" per request
  local i
  for i in $(seq 1 "$N"); do
    curl -s -o /dev/null --max-time 30 \
      -H "Authorization: Bearer $token" \
      -w '%{http_code} %{time_total}\n' \
      "$BASE_URL$1" >> "$2" || echo "000 30.0" >> "$2"
  done
}

echo "endpoint,count,errors,p50_ms,p95_ms,p99_ms,max_ms"
echo "$ENDPOINTS" | while IFS='|' read -r name path; do
  [ -n "$name" ] || continue
  : > "$tmpdir/$name"
  # warm-up request (JIT / caches) — not measured
  curl -s -o /dev/null --max-time 30 -H "Authorization: Bearer $token" "$BASE_URL$path" || true

  if [ "$CONCURRENCY" -gt 1 ]; then
    for _ in $(seq 1 "$CONCURRENCY"); do run_one "$path" "$tmpdir/$name.$_" & done
    wait
    cat "$tmpdir/$name".* > "$tmpdir/$name" 2>/dev/null || true
  else
    run_one "$path" "$tmpdir/$name"
  fi

  awk -v name="$name" '
    { total++; if ($1 < 200 || $1 >= 300) errors++; else times[n++] = $2 * 1000 }
    END {
      if (n == 0) { printf "%s,%d,%d,,,,\n", name, total, errors; exit }
      # simple insertion sort — n is small
      for (i = 1; i < n; i++) { v = times[i]; j = i - 1
        while (j >= 0 && times[j] > v) { times[j+1] = times[j]; j-- } times[j+1] = v }
      p50 = times[int(n * 0.50)]; p95 = times[int(n * 0.95)]
      p99 = times[int(n * 0.99)]; max = times[n-1]
      printf "%s,%d,%d,%.0f,%.0f,%.0f,%.0f\n", name, total, errors, p50, p95, p99, max
    }' "$tmpdir/$name"
done
