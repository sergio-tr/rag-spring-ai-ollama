CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Default monitoring login for the OTEL postgresql receiver (password synced from db/.env in 01-monitor-user.sh).
DO $create_exporter$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres_exporter') THEN
    CREATE ROLE postgres_exporter WITH LOGIN PASSWORD 'postgres_exporter';
  END IF;
END
$create_exporter$;

GRANT pg_monitor TO postgres_exporter;
