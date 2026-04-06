-- Optional FK: resolved_config_snapshot_id on knowledge_index_snapshot (nullable).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_knowledge_index_snapshot_resolved_config'
    ) THEN
        ALTER TABLE knowledge_index_snapshot
            ADD CONSTRAINT fk_knowledge_index_snapshot_resolved_config
            FOREIGN KEY (resolved_config_snapshot_id)
            REFERENCES resolved_config_snapshot (id)
            ON DELETE SET NULL;
    END IF;
END $$;
