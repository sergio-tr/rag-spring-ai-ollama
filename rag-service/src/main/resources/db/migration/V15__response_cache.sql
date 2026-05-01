-- Semantic response cache (optional embedding for similarity lookup).

CREATE TABLE response_cache (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects (id) ON DELETE CASCADE,
    query_hash VARCHAR(128) NOT NULL,
    query_text TEXT,
    response_text TEXT,
    embedding vector(1024),
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ
);

CREATE INDEX idx_response_cache_query_hash ON response_cache (query_hash);
CREATE INDEX idx_response_cache_user_project ON response_cache (user_id, project_id);
