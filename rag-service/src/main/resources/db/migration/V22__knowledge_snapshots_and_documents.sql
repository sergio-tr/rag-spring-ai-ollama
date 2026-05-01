-- Logical index snapshots, document artifacts, reindex events, extended project_documents.

-- (1) knowledge_index_snapshot — no incoming FK from project_documents yet.
CREATE TABLE knowledge_index_snapshot (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    signature_hash VARCHAR(128) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    project_id UUID REFERENCES projects (id) ON DELETE CASCADE,
    conversation_id UUID REFERENCES conversations (id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL CHECK (status IN ('BUILDING', 'ACTIVE', 'SUPERSEDED', 'FAILED')),
    resolved_config_snapshot_id UUID,
    resolved_config_hash VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_knowledge_index_snapshot_project_status ON knowledge_index_snapshot (project_id, status);
CREATE INDEX idx_knowledge_index_snapshot_conversation_status ON knowledge_index_snapshot (conversation_id, status);

-- (2) knowledge_snapshot_document
CREATE TABLE knowledge_snapshot_document (
    snapshot_id UUID NOT NULL REFERENCES knowledge_index_snapshot (id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES project_documents (id) ON DELETE CASCADE,
    PRIMARY KEY (snapshot_id, document_id)
);

CREATE INDEX idx_knowledge_snapshot_document_document ON knowledge_snapshot_document (document_id);

-- (3) document_artifact
CREATE TABLE document_artifact (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL REFERENCES project_documents (id) ON DELETE CASCADE,
    artifact_type VARCHAR(32) NOT NULL,
    payload_jsonb JSONB NOT NULL DEFAULT '{}',
    content_hash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_document_artifact_document_type ON document_artifact (document_id, artifact_type);

-- (4) reindex_event
CREATE TABLE reindex_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID REFERENCES project_documents (id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects (id) ON DELETE SET NULL,
    conversation_id UUID REFERENCES conversations (id) ON DELETE SET NULL,
    reason VARCHAR(64) NOT NULL,
    target_signature_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    async_task_id UUID REFERENCES async_task (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reindex_event_status_created ON reindex_event (status, created_at);

-- (5) ALTER project_documents
ALTER TABLE project_documents
    ADD COLUMN corpus_scope VARCHAR(32) NOT NULL DEFAULT 'PROJECT_SHARED'
        CHECK (corpus_scope IN ('PROJECT_SHARED', 'CHAT_LOCAL')),
    ADD COLUMN conversation_id UUID REFERENCES conversations (id) ON DELETE SET NULL,
    ADD COLUMN storage_uri TEXT,
    ADD COLUMN content_checksum VARCHAR(64),
    ADD COLUMN mime_type VARCHAR(128),
    ADD COLUMN byte_size BIGINT,
    ADD COLUMN current_index_snapshot_id UUID REFERENCES knowledge_index_snapshot (id) ON DELETE SET NULL,
    ADD COLUMN requires_reindex BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_project_documents_conversation_scope ON project_documents (conversation_id, corpus_scope)
    WHERE conversation_id IS NOT NULL;
