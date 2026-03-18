-- Flyway migration for initial application schema (documents + vector_store).

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

CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_date ON vector_store USING GIN ((metadata->>'date'));
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_president ON vector_store USING GIN ((metadata->>'president'));
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_document_id ON vector_store USING GIN ((metadata->>'document_id'));
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_id ON vector_store USING GIN ((metadata->>'id'));
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_filename ON vector_store USING GIN ((metadata->>'filename'));

CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_date_president ON vector_store
    USING GIN ((metadata->>'date'), (metadata->>'president'));

