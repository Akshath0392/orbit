DO $$
BEGIN
  CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
EXCEPTION WHEN insufficient_privilege THEN
  RAISE NOTICE 'pg_stat_statements not created (needs superuser) — profiling views unavailable on this environment';
END $$;
