-- Align postgres_exporter password with repository defaults (db/.env.example, observability/.env.example).
-- Fixes OTEL postgresql receiver "password authentication failed" when OBS_PG_EXPORTER_PASSWORD was set but the DB
-- role still used another secret (e.g. role created earlier or only POSTGRES_MONITOR_* differed from OBS_*).
-- If you use a custom exporter password, set POSTGRES_MONITOR_PASSWORD and OBS_PG_EXPORTER_PASSWORD to the same value and ALTER ROLE manually instead of relying on this migration.

ALTER ROLE postgres_exporter PASSWORD 'postgres_exporter';
