-- Read-only metrics role for OpenTelemetry postgresql receiver (observability/otel-collector).
-- Runs on existing DBs where docker-entrypoint-initdb.d did not create the role (e.g. volume created before db/init shipped 00-extensions.sql / 01-monitor-user.sh).
-- Default password matches db/.env.example and observability/.env.example; change both env files + ALTER ROLE if you rotate it.

DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres_exporter') THEN
        CREATE ROLE postgres_exporter LOGIN PASSWORD 'postgres_exporter';
    END IF;
END
$$;

GRANT pg_monitor TO postgres_exporter;
