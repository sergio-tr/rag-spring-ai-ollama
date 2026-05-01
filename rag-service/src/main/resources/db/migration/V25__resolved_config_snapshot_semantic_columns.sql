-- Add semantic columns for resolved configuration snapshots.

ALTER TABLE resolved_config_snapshot ADD COLUMN IF NOT EXISTS reindex_impact_jsonb JSONB;
ALTER TABLE resolved_config_snapshot ADD COLUMN IF NOT EXISTS system_prompt_layers_jsonb JSONB;
ALTER TABLE resolved_config_snapshot ADD COLUMN IF NOT EXISTS effective_system_prompt TEXT;
