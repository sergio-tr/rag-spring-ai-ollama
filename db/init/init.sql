CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DROP TABLE IF EXISTS vector_store CASCADE;
DROP TABLE IF EXISTS documents CASCADE;

CREATE TABLE documents (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    document_name TEXT UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

CREATE TABLE vector_store (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(1024),  -- sized for Gemma 3:4b embeddings
    chunk_index INTEGER,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_document_name ON documents(document_name);
CREATE INDEX idx_vector_store_embedding ON vector_store USING HNSW (embedding vector_cosine_ops);

-- These indexes significantly improve metadata search performance
CREATE INDEX idx_vector_store_metadata_date ON vector_store USING GIN ((metadata->>'date'));
CREATE INDEX idx_vector_store_metadata_president ON vector_store USING GIN ((metadata->>'president'));
CREATE INDEX idx_vector_store_metadata_document_id ON vector_store USING GIN ((metadata->>'document_id'));
CREATE INDEX idx_vector_store_metadata_id ON vector_store USING GIN ((metadata->>'id'));
CREATE INDEX idx_vector_store_metadata_filename ON vector_store USING GIN ((metadata->>'filename'));

-- Composite index for common searches (date + president)
CREATE INDEX idx_vector_store_metadata_date_president ON vector_store
    USING GIN ((metadata->>'date'), (metadata->>'president'));

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
