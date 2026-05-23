-- LAB repair (Agent D): canonical 6-model allowlist, demote bge-m3, add qwen3-embedding, :latest embedding tags.

-- bge-m3 is no longer recommended or in the default allowlist (historical V59 row kept but disabled).
UPDATE allowed_model
SET in_allowlist = FALSE,
    available = FALSE
WHERE lower(name) IN ('bge-m3', 'bge-m3:latest');

DELETE FROM allowed_model
WHERE lower(name) IN ('bge-m3', 'bge-m3:latest')
  AND type = 'LLM';

-- Canonical allowlist (3 embedding + 3 LLM).
INSERT INTO allowed_model (name, type, in_allowlist, available, display_name)
VALUES
    ('mxbai-embed-large:latest', 'EMBEDDING', TRUE, FALSE, 'MxBai Embed Large'),
    ('nomic-embed-text:latest', 'EMBEDDING', TRUE, FALSE, 'Nomic Embed Text'),
    ('qwen3-embedding:latest', 'EMBEDDING', TRUE, FALSE, 'Qwen3 Embedding'),
    ('gemma3:4b', 'LLM', TRUE, FALSE, 'Gemma3 4B'),
    ('mistral:7b', 'LLM', TRUE, FALSE, 'Mistral 7B'),
    ('llama3.1:8b', 'LLM', TRUE, FALSE, 'Llama 3.1 8B')
ON CONFLICT (name, type) DO UPDATE
SET in_allowlist = EXCLUDED.in_allowlist,
    display_name = COALESCE(allowed_model.display_name, EXCLUDED.display_name);

-- Retire untagged embedding aliases when :latest canonical rows exist.
UPDATE allowed_model
SET in_allowlist = FALSE,
    available = FALSE
WHERE type = 'EMBEDDING'
  AND lower(name) IN ('mxbai-embed-large', 'nomic-embed-text', 'qwen3-embedding')
  AND EXISTS (
      SELECT 1
      FROM allowed_model am2
      WHERE am2.type = 'EMBEDDING'
        AND am2.in_allowlist = TRUE
        AND lower(am2.name) = lower(allowed_model.name) || ':latest'
  );

-- Product default embedding tag (mxbai-embed-large -> mxbai-embed-large:latest).
UPDATE project_index_profile
SET embedding_model_id = 'mxbai-embed-large:latest',
    profile_hash = md5(
        materialization_strategy::text || '|' ||
        metadata_enabled::text || '|' ||
        COALESCE(metadata_profile, '') || '|' ||
        'mxbai-embed-large:latest' || '|' ||
        chunk_max_chars::text || '|' ||
        COALESCE(chunk_overlap::text, '')
    )
WHERE embedding_model_id = 'mxbai-embed-large';

UPDATE knowledge_index_snapshot
SET index_profile_jsonb = jsonb_set(
        index_profile_jsonb,
        '{embeddingModelId}',
        '"mxbai-embed-large:latest"'::jsonb,
        true
    )
WHERE index_profile_jsonb ->> 'embeddingModelId' = 'mxbai-embed-large';

-- New projects inherit the canonical default embedding tag.
CREATE OR REPLACE FUNCTION ensure_project_index_profile_on_project_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO project_index_profile (
        project_id,
        materialization_strategy,
        metadata_enabled,
        metadata_profile,
        embedding_model_id,
        chunk_max_chars,
        chunk_overlap,
        profile_hash,
        created_at,
        updated_at
    )
    VALUES (
        NEW.id,
        'CHUNK_LEVEL',
        FALSE,
        NULL,
        'mxbai-embed-large:latest',
        400,
        NULL,
        md5('CHUNK_LEVEL|false||mxbai-embed-large:latest|400|'),
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (project_id) DO NOTHING;
    RETURN NEW;
END;
$$;
