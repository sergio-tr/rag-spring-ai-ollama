-- Every knowledge_index_snapshot row must reference the resolved configuration identity.
DELETE FROM knowledge_index_snapshot
WHERE resolved_config_snapshot_id IS NULL
   OR resolved_config_hash IS NULL
   OR btrim(resolved_config_hash) = '';

ALTER TABLE knowledge_index_snapshot
    ALTER COLUMN resolved_config_snapshot_id SET NOT NULL;

ALTER TABLE knowledge_index_snapshot
    ALTER COLUMN resolved_config_hash SET NOT NULL;
