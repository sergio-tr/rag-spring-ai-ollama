-- System-wide defaults (single row) and per-user / per-project RAG configuration rows.

CREATE TABLE default_system_configuration (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    values JSONB NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rag_configuration (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects (id) ON DELETE CASCADE,
    level VARCHAR(32) NOT NULL CHECK (level IN ('USER_DEFAULT', 'PROJECT')),
    name VARCHAR(255) NOT NULL,
    values JSONB NOT NULL DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rag_configuration_user ON rag_configuration (user_id);
CREATE INDEX idx_rag_configuration_project ON rag_configuration (project_id);

-- One active USER_DEFAULT row per user (project_id IS NULL).
CREATE UNIQUE INDEX uniq_rag_configuration_user_default
    ON rag_configuration (user_id)
    WHERE level = 'USER_DEFAULT' AND project_id IS NULL;

-- One active PROJECT row per (user, project) when scoped to a project.
CREATE UNIQUE INDEX uniq_rag_configuration_project_scope
    ON rag_configuration (user_id, project_id)
    WHERE level = 'PROJECT' AND project_id IS NOT NULL;
