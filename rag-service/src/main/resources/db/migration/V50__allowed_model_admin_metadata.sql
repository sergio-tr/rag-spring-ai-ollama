-- Admin allowlist model availability metadata (Ollama validation + pull status).

ALTER TABLE allowed_model
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS tags_json JSONB,
    ADD COLUMN IF NOT EXISTS available BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_checked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_pull_status VARCHAR(64),
    ADD COLUMN IF NOT EXISTS last_pull_error TEXT;

-- Backfill: if a row had installed_at, consider it available by default.
UPDATE allowed_model
SET available = TRUE
WHERE installed_at IS NOT NULL AND available = FALSE;

