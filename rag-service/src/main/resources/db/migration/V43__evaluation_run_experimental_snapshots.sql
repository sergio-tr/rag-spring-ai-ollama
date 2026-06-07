-- Phase 5: immutable experimental snapshots + embedding downstream RAG flag on canonical runs.

ALTER TABLE evaluation_run
    ADD COLUMN IF NOT EXISTS llm_experimental_snapshot jsonb;

ALTER TABLE evaluation_run
    ADD COLUMN IF NOT EXISTS embedding_experimental_snapshot jsonb;

ALTER TABLE evaluation_run
    ADD COLUMN IF NOT EXISTS prompt_profile_snapshot jsonb;

ALTER TABLE evaluation_run
    ADD COLUMN IF NOT EXISTS embedding_downstream_rag boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN evaluation_run.llm_experimental_snapshot IS 'Lab baseline: immutable LLM generation params (Phase 5)';
COMMENT ON COLUMN evaluation_run.embedding_experimental_snapshot IS 'Lab baseline: immutable embedding params snapshot (Phase 5)';
COMMENT ON COLUMN evaluation_run.prompt_profile_snapshot IS 'Lab baseline: versioned prompt fragments + effective system hash (Phase 5)';
COMMENT ON COLUMN evaluation_run.embedding_downstream_rag IS 'When true, EMBEDDING_RETRIEVAL runs retrieval then fixed-LLM answer (Phase 5)';
