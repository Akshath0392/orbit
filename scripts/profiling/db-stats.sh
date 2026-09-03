#!/usr/bin/env bash
# Postgres health + hot-query snapshot for a release profiling report.
# Connects via docker exec (container orbit-postgres) when available,
# otherwise falls back to local psql with PG* env vars.
# See docs/plan/release-performance-profiling-plan.md
set -uo pipefail

PGDB="${ORBIT_PG_DB:-orbit}"
PGUSER_="${ORBIT_PG_USER:-orbit}"

if docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^orbit-postgres$'; then
  run_sql() { docker exec -i orbit-postgres psql -U "$PGUSER_" -d "$PGDB" -X -P pager=off -c "$1"; }
else
  export PGPASSWORD="${PGPASSWORD:-${ORBIT_PG_PASSWORD:-orbit_secret}}"
  run_sql() { psql -h "${ORBIT_PG_HOST:-localhost}" -U "$PGUSER_" -d "$PGDB" -X -P pager=off -c "$1"; }
fi

section() { printf '\n===== %s =====\n' "$1"; }

section "DATABASE SIZE"
run_sql "SELECT pg_size_pretty(pg_database_size('$PGDB')) AS db_size;"

section "TOP 15 RELATIONS BY TOTAL SIZE"
run_sql "SELECT relname, pg_size_pretty(pg_total_relation_size(c.oid)) AS total,
                pg_size_pretty(pg_relation_size(c.oid)) AS table_only, reltuples::bigint AS est_rows
         FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public' AND c.relkind = 'r'
         ORDER BY pg_total_relation_size(c.oid) DESC LIMIT 15;"

section "CACHE HIT RATIO (want > 0.99)"
run_sql "SELECT round(sum(blks_hit)::numeric / nullif(sum(blks_hit) + sum(blks_read), 0), 4) AS cache_hit_ratio
         FROM pg_stat_database WHERE datname = '$PGDB';"

section "SEQ-SCAN OFFENDERS (large tables being seq-scanned — index candidates)"
run_sql "SELECT relname, seq_scan, idx_scan, n_live_tup
         FROM pg_stat_user_tables
         WHERE n_live_tup > 10000 AND seq_scan > idx_scan
         ORDER BY seq_scan DESC LIMIT 10;"

section "CONNECTIONS"
run_sql "SELECT state, count(*) FROM pg_stat_activity WHERE datname = '$PGDB' GROUP BY state;"

section "TOP 20 STATEMENTS BY TOTAL TIME (pg_stat_statements)"
run_sql "SELECT round(total_exec_time)::bigint AS total_ms, calls,
                round(mean_exec_time, 1) AS mean_ms, rows,
                left(regexp_replace(query, '\s+', ' ', 'g'), 110) AS query
         FROM pg_stat_statements
         ORDER BY total_exec_time DESC LIMIT 20;" \
  || echo "pg_stat_statements not installed — enable per infra plan §5 (shared_preload_libraries + CREATE EXTENSION)."
