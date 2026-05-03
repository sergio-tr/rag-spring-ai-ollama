-- Mandatory linkage from reindex_event to resolved_config_snapshot.
ALTER TABLE reindex_event
    ADD COLUMN IF NOT EXISTS resolved_config_snapshot_id UUID REFERENCES resolved_config_snapshot (id) ON DELETE RESTRICT;

-- Pre-release rows cannot be linked deterministically; drop audit rows rather than fabricating FK targets.
DELETE FROM reindex_event;

ALTER TABLE reindex_event
    ALTER COLUMN resolved_config_snapshot_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_reindex_event_resolved_config_snapshot
    ON reindex_event (resolved_config_snapshot_id);
