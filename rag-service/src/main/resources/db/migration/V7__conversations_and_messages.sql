-- Chat threads and messages (snapshots for reproducibility).

CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    title VARCHAR(512),
    config_id UUID REFERENCES rag_configuration (id) ON DELETE SET NULL,
    preset_id UUID REFERENCES rag_preset (id) ON DELETE SET NULL,
    llm_model VARCHAR(255),
    classifier_model_id VARCHAR(255),
    document_filter JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conversations_user ON conversations (user_id);
CREATE INDEX idx_conversations_project ON conversations (project_id);

CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content TEXT NOT NULL,
    sources JSONB,
    query_type VARCHAR(64),
    trace_id VARCHAR(128),
    pipeline_steps JSONB,
    grounding_score DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id);
