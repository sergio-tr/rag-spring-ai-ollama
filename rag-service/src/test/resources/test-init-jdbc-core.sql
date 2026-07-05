-- JDBC integration schema for local Postgres fallback (ScriptUtils-safe; no DO $$ blocks).
-- Testcontainers continues to use test-init.sql, which also creates postgres_exporter.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS documents (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    document_name TEXT UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

CREATE TABLE IF NOT EXISTS vector_store (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(1024),
    chunk_index INTEGER,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_documents_document_name ON documents(document_name);
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding ON vector_store USING HNSW (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_date ON vector_store ((metadata->>'date'));
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_president ON vector_store ((metadata->>'president'));
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_document_id ON vector_store ((metadata->>'document_id'));
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_id ON vector_store ((metadata->>'id'));
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_filename ON vector_store ((metadata->>'filename'));

CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_date_president ON vector_store
    ((metadata->>'date'), (metadata->>'president'));

ALTER TABLE vector_store
    ADD COLUMN IF NOT EXISTS project_id UUID;

ALTER TABLE vector_store
    ADD COLUMN IF NOT EXISTS content_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED;

CREATE INDEX IF NOT EXISTS idx_vector_store_content_tsv ON vector_store USING GIN (content_tsv);
