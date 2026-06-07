-- Lab evaluation corpus ownership for knowledge_index_snapshot rows.
-- Snapshots may belong to a project (chat/product) or an evaluation corpus (Lab benchmarks).

ALTER TABLE knowledge_index_snapshot
    ADD COLUMN IF NOT EXISTS owner_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS owner_id UUID;

COMMENT ON COLUMN knowledge_index_snapshot.owner_type IS 'Snapshot owner scope: PROJECT or EVALUATION_CORPUS.';
COMMENT ON COLUMN knowledge_index_snapshot.owner_id IS 'Owner id: project_id when owner_type=PROJECT, evaluation_corpus.id when EVALUATION_CORPUS.';

-- Backfill legacy rows as project-owned snapshots.
UPDATE knowledge_index_snapshot
SET owner_type = 'PROJECT',
    owner_id = project_id
WHERE owner_type IS NULL
  AND project_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_knowledge_index_snapshot_owner_status
    ON knowledge_index_snapshot (owner_type, owner_id, status);
