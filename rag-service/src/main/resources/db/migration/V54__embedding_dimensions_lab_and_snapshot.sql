-- Persist embedding output dimension for knowledge snapshots and Lab evaluation runs (pgvector column width is fixed separately).
ALTER TABLE knowledge_index_snapshot
    ADD COLUMN IF NOT EXISTS embedding_dimensions INTEGER NULL;

COMMENT ON COLUMN knowledge_index_snapshot.embedding_dimensions IS 'Ollama embedding output dimension observed when the snapshot was created; must match vector_store.embedding typmod.';

ALTER TABLE evaluation_run
    ADD COLUMN IF NOT EXISTS embedding_dimensions INTEGER NULL;

COMMENT ON COLUMN evaluation_run.embedding_dimensions IS 'Embedding vector dimension for this Lab run (aligned with index snapshot / physical store).';
