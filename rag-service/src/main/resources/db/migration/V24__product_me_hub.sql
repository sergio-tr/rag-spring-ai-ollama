-- Product hub: schema versioning for prefs/personalization, project visual identity, account export artifacts, indexes for /me/documents.

ALTER TABLE user_preferences
    ADD COLUMN IF NOT EXISTS schema_version INT NOT NULL DEFAULT 1;

ALTER TABLE user_personalization
    ADD COLUMN IF NOT EXISTS schema_version INT NOT NULL DEFAULT 1;

ALTER TABLE projects ADD COLUMN IF NOT EXISTS color_hex VARCHAR(7);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS icon_key VARCHAR(64);

-- Global user document inventory (join projects.owner_id): speed up filters by project and ordering.
CREATE INDEX IF NOT EXISTS idx_project_documents_project_uploaded
    ON project_documents (project_id, uploaded_at DESC);

CREATE TABLE account_export_artifact (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    async_task_id UUID REFERENCES async_task (id) ON DELETE SET NULL,
    storage_uri TEXT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    downloaded_at TIMESTAMPTZ
);

CREATE INDEX idx_account_export_artifact_user_created ON account_export_artifact (user_id, created_at DESC);
CREATE INDEX idx_account_export_artifact_expires ON account_export_artifact (expires_at)
    WHERE status IN ('READY', 'PENDING');
