-- Generated tsvector + GIN for PostgreSQL full-text sparse retrieval (hybrid RAG).

ALTER TABLE vector_store
    ADD COLUMN IF NOT EXISTS content_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED;

CREATE INDEX IF NOT EXISTS idx_vector_store_content_tsv ON vector_store USING GIN (content_tsv);
