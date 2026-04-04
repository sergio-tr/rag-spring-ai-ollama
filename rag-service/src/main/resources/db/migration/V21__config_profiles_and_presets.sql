-- Block 1: versioned config profiles, preset composition refs, resolved snapshots, user prefs, project prompt, chat runtime override.

CREATE TABLE config_profile (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_type VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    label VARCHAR(255),
    payload JSONB NOT NULL DEFAULT '{}',
    owner_id UUID REFERENCES users (id) ON DELETE CASCADE,
    immutable BOOLEAN NOT NULL DEFAULT FALSE,
    content_hash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_config_profile_type ON config_profile (profile_type);
CREATE INDEX idx_config_profile_owner ON config_profile (owner_id);

ALTER TABLE rag_preset ADD COLUMN IF NOT EXISTS composition_version INT NOT NULL DEFAULT 0;

CREATE TABLE rag_preset_profile_ref (
    preset_id UUID NOT NULL REFERENCES rag_preset (id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES config_profile (id) ON DELETE CASCADE,
    ordinal INT NOT NULL,
    role VARCHAR(64),
    PRIMARY KEY (preset_id, profile_id)
);

CREATE UNIQUE INDEX uq_rag_preset_profile_ref_ordinal ON rag_preset_profile_ref (preset_id, ordinal);

CREATE TABLE resolved_config_snapshot (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID REFERENCES conversations (id) ON DELETE SET NULL,
    message_id UUID REFERENCES messages (id) ON DELETE SET NULL,
    job_id UUID REFERENCES async_task (id) ON DELETE SET NULL,
    payload_jsonb JSONB NOT NULL DEFAULT '{}',
    capability_set_jsonb JSONB,
    compatibility_result_jsonb JSONB,
    prompt_stack_preview_jsonb JSONB,
    provenance_jsonb JSONB,
    config_hash VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_resolved_config_snapshot_conv ON resolved_config_snapshot (conversation_id);
CREATE INDEX idx_resolved_config_snapshot_msg ON resolved_config_snapshot (message_id);

CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    preferences_jsonb JSONB NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_personalization (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    personalization_jsonb JSONB NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE projects ADD COLUMN IF NOT EXISTS project_prompt TEXT;

ALTER TABLE conversations ADD COLUMN IF NOT EXISTS runtime_override_jsonb JSONB NOT NULL DEFAULT '{}';
