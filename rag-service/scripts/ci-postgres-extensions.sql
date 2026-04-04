-- Same extensions as .github/workflows/ci.yml "Prepare Postgres" (vectordb).
-- Loaded via psql -f to avoid shell quoting issues (especially PowerShell + docker exec on Windows).

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
