-- Canonical benchmark runs: dataset storage metadata, fingerprints, async_task link.

-- evaluation_dataset: ownership scope and binary storage metadata
ALTER TABLE evaluation_dataset
    ADD COLUMN IF NOT EXISTS dataset_scope VARCHAR(32) NOT NULL DEFAULT 'USER_DATASET',
    ADD COLUMN IF NOT EXISTS storage_uri TEXT,
    ADD COLUMN IF NOT EXISTS byte_size BIGINT,
    ADD COLUMN IF NOT EXISTS mime_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS schema_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS benchmark_kinds_allowed JSONB;

COMMENT ON COLUMN evaluation_dataset.dataset_scope IS 'SYSTEM_DATASET | USER_DATASET';

-- evaluation_run: benchmark metadata and reproducibility (canonical run table)
ALTER TABLE evaluation_run
    ADD COLUMN IF NOT EXISTS benchmark_kind VARCHAR(64),
    ADD COLUMN IF NOT EXISTS run_kind VARCHAR(32),
    ADD COLUMN IF NOT EXISTS workflow_schema_version VARCHAR(32),
    ADD COLUMN IF NOT EXISTS dataset_sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS resolved_config_snapshot_id UUID REFERENCES resolved_config_snapshot (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS index_snapshot_id UUID REFERENCES knowledge_index_snapshot (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS index_signature_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS preset_id UUID REFERENCES rag_preset (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS llm_model_id VARCHAR(256),
    ADD COLUMN IF NOT EXISTS embedding_model_id VARCHAR(256),
    ADD COLUMN IF NOT EXISTS classifier_model_id VARCHAR(256),
    ADD COLUMN IF NOT EXISTS async_task_id UUID REFERENCES async_task (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS aggregates_json JSONB;

CREATE INDEX IF NOT EXISTS idx_evaluation_run_user_created ON evaluation_run (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_evaluation_run_benchmark ON evaluation_run (benchmark_kind);
CREATE INDEX IF NOT EXISTS idx_evaluation_run_async_task ON evaluation_run (async_task_id);

-- evaluation_result: per-item metrics (JSONB) and benchmark kind denormalized
ALTER TABLE evaluation_result
    ADD COLUMN IF NOT EXISTS benchmark_kind VARCHAR(64),
    ADD COLUMN IF NOT EXISTS metrics_payload JSONB;

COMMENT ON COLUMN evaluation_run.async_task_id IS 'Operational link; canonical state is on evaluation_run';
COMMENT ON COLUMN evaluation_result.metrics_payload IS 'Kind-specific metrics (judge scores, recall@k, etc.)';
