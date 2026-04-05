-- Chat: message ordering, lifecycle, soft delete, execution metadata, persisted drafts, job-backed generation.

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS seq INTEGER,
    ADD COLUMN IF NOT EXISTS status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS execution_metadata JSONB;

-- Backfill sequence per conversation (stable ordering for edit/truncate).
WITH numbered AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY conversation_id ORDER BY created_at) AS rn
    FROM messages
)
UPDATE messages m
SET seq = numbered.rn
FROM numbered
WHERE m.id = numbered.id;

ALTER TABLE messages ALTER COLUMN seq SET NOT NULL;

UPDATE messages SET status = 'DONE' WHERE status IS NULL;

ALTER TABLE messages ALTER COLUMN status SET NOT NULL;

ALTER TABLE messages
    ADD CONSTRAINT uq_messages_conversation_seq UNIQUE (conversation_id, seq);

CREATE TABLE conversation_draft (
    conversation_id UUID PRIMARY KEY REFERENCES conversations (id) ON DELETE CASCADE,
    content TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
