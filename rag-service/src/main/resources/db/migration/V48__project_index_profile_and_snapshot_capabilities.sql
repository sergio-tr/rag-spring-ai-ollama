-- Project index profile and snapshot capabilities linkage.
-- Separates index-time (project/profile) decisions from per-conversation runtime overrides.

-- (1) project_index_profile: one row per project.
CREATE TABLE IF NOT EXISTS project_index_profile (
    project_id UUID PRIMARY KEY REFERENCES projects (id) ON DELETE CASCADE,
    materialization_strategy VARCHAR(32) NOT NULL,
    metadata_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    metadata_profile VARCHAR(64),
    embedding_model_id VARCHAR(128) NOT NULL,
    chunk_max_chars INT NOT NULL,
    chunk_overlap INT,
    profile_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_index_profile_hash ON project_index_profile (profile_hash);

-- (2) knowledge_index_snapshot: persist index/profile capabilities used to build the snapshot.
ALTER TABLE knowledge_index_snapshot
    ADD COLUMN IF NOT EXISTS index_profile_jsonb JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS index_profile_hash VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_knowledge_index_snapshot_profile_hash ON knowledge_index_snapshot (index_profile_hash);

-- (3) Backfill project_index_profile for existing projects.
-- We cannot read application properties at migration time, so we backfill with documented defaults.
-- profile_hash uses md5 for portability (no pgcrypto dependency).
INSERT INTO project_index_profile (
    project_id,
    materialization_strategy,
    metadata_enabled,
    metadata_profile,
    embedding_model_id,
    chunk_max_chars,
    chunk_overlap,
    profile_hash,
    created_at,
    updated_at
)
SELECT
    p.id,
    'CHUNK_LEVEL',
    FALSE,
    NULL,
    'mxbai-embed-large',
    400,
    NULL,
    md5('CHUNK_LEVEL|false||mxbai-embed-large|400|'),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM projects p
WHERE NOT EXISTS (SELECT 1 FROM project_index_profile pip WHERE pip.project_id = p.id);

-- (4) Backfill snapshot capabilities for existing snapshots where missing.
UPDATE knowledge_index_snapshot s
SET
    index_profile_jsonb =
        jsonb_build_object(
            'materializationStrategy', 'CHUNK_LEVEL',
            'supportsMetadata', false,
            'metadataProfile', NULL,
            'embeddingModelId', 'mxbai-embed-large',
            'chunkMaxChars', 400,
            'chunkOverlap', NULL
        ),
    index_profile_hash = md5('CHUNK_LEVEL|false||mxbai-embed-large|400|'),
    updated_at = CURRENT_TIMESTAMP
WHERE (s.index_profile_hash IS NULL OR s.index_profile_hash = '')
  AND (s.index_profile_jsonb IS NULL OR s.index_profile_jsonb = '{}'::jsonb);

