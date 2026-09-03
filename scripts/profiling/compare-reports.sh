#!/usr/bin/env bash
# Compares two release profiling reports and flags regressions.
#   Usage: compare-reports.sh <old-report-dir> <new-report-dir>
# Exit code 1 if any endpoint is >20% slower at p95 or has new errors —
# use as the release gate before widening feature flags.
# See docs/plan/release-performance-profiling-plan.md
set -euo pipefail

OLD="${1:?usage: compare-reports.sh <old-report-dir> <new-report-dir>}"
NEW="${2:?usage: compare-reports.sh <old-report-dir> <new-report-dir>}"
THRESHOLD_PCT="${ORBIT_REGRESSION_PCT:-20}"

for d in "$OLD" "$NEW"; do
  [ -f "$d/api-benchmark.csv" ] || { echo "ERROR: $d/api-benchmark.csv not found" >&2; exit 2; }
done

echo "Comparing p95 latency: $(basename "$OLD") → $(basename "$NEW")  (threshold ${THRESHOLD_PCT}%)"
echo

awk -F, -v thr="$THRESHOLD_PCT" '
  FNR == 1 { next }                                # skip headers
  NR == FNR { old_p95[$1] = $4; old_err[$1] = $3; next }
  {
    name = $1; new_err = $3; new_p95 = $4
    if (!(name in old_p95)) { printf "  NEW   %-12s p95 %sms (no baseline)\n", name, new_p95; next }
    o = old_p95[name] + 0; n = new_p95 + 0
    delta = (o > 0) ? (n - o) * 100 / o : 0
    flag = ""
    if (new_err + 0 > old_err[name] + 0) { flag = "ERRORS"; bad = 1 }
    else if (delta > thr)                { flag = "SLOWER"; bad = 1 }
    else if (delta < -thr)               { flag = "faster" }
    printf "  %-6s%-12s p95 %4.0fms → %4.0fms  (%+.0f%%)  errors %s → %s\n",
           flag, name, o, n, delta, old_err[name], new_err
  }
  END { exit bad ? 1 : 0 }
' "$OLD/api-benchmark.csv" "$NEW/api-benchmark.csv"
rc=$?

echo
if [ $rc -ne 0 ]; then
  echo "RESULT: REGRESSION — investigate before widening any feature flag."
  echo "        Start with the new report'\''s db-stats.txt (top statements / seq scans)."
else
  echo "RESULT: OK — no p95 regression beyond ${THRESHOLD_PCT}%, no new errors."
fi
exit $rc
