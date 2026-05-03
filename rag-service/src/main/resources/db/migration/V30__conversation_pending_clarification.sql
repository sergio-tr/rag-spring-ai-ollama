ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS pending_clarification_jsonb jsonb;
