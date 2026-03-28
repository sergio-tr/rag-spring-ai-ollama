-- DEV-ONLY reset script.
-- Drops application tables (`documents`, `vector_store`) so the schema matches the current RAG config.
-- DO NOT use this in prod local / prod: it is destructive and not coordinated with migrations.

DROP TABLE IF EXISTS vector_store CASCADE;
DROP TABLE IF EXISTS documents CASCADE;

