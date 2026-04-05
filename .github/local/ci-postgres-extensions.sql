-- Same extensions as .github/workflows/ci.yml "Prepare Postgres" (vectordb).
-- Loaded via psql -f to avoid shell quoting issues.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
