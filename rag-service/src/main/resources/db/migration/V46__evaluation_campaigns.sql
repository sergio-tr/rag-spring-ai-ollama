-- Phase 6: Multi-model evaluation campaigns (group multiple evaluation_run rows under one campaign).

CREATE TABLE IF NOT EXISTS evaluation_campaign (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    project_id UUID NULL REFERENCES projects(id),
    study_type VARCHAR(64) NOT NULL,
    name TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    -- Free-form metadata for reproducibility (requested models, flags, etc.)
    meta_json JSONB NULL
);

ALTER TABLE evaluation_run
    ADD COLUMN IF NOT EXISTS campaign_id UUID NULL REFERENCES evaluation_campaign(id);

CREATE INDEX IF NOT EXISTS idx_evaluation_run_campaign_id ON evaluation_run(campaign_id);

