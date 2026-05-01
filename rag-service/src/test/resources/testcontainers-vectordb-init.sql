-- Applied to the primary DB when starting Postgres via Testcontainers for @SpringBootTest (local dev).
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
