-- Project documents (ingestion state) and optional project scope on vector chunks.

CREATE TABLE project_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    file_name TEXT NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('INGESTING', 'READY', 'ERROR')),
    chunk_count INTEGER,
    error_message TEXT,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reindexed_at TIMESTAMPTZ
);

CREATE INDEX idx_project_documents_project_id ON project_documents (project_id);

-- Existing rows keep NULL until re-ingestion assigns a project scope.
ALTER TABLE vector_store ADD COLUMN IF NOT EXISTS project_id UUID REFERENCES projects (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_vector_store_project_id ON vector_store (project_id);
