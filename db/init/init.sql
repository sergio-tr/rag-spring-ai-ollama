CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Read-only monitoring user for PostgreSQL metrics (OpenTelemetry Collector).
DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres_exporter') THEN
        CREATE USER postgres_exporter WITH PASSWORD 'postgres_exporter';
        GRANT pg_monitor TO postgres_exporter;
    END IF;
END
$$;
