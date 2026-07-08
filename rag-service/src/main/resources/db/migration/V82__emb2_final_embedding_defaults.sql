-- EMB-2: apply evaluated embedding defaults (bge-m3 / wide_chunk) to project profiles and system config.
-- Existing knowledge_index_snapshot rows are unchanged; reindex required to bind new embedding vectors.

-- System-wide RAG defaults (topK, threshold, embedding options).
UPDATE default_system_configuration dsc
SET values = COALESCE(dsc.values, '{}'::jsonb)
    || '{
        "topK": 12,
        "similarityThreshold": 0.25,
        "materializationStrategy": "CHUNK_LEVEL",
        "embeddingModel": "bge-m3",
        "embeddingDimensions": 1024,
        "embeddingNormalize": true,
        "embeddingBatchSize": 32,
        "embeddingTimeoutSeconds": 60
    }'::jsonb,
    updated_at = NOW();

-- Project index profile default embedding model (deployment-wide product default).
UPDATE project_index_profile
SET embedding_model_id = 'bge-m3',
    profile_hash = md5(
        materialization_strategy::text || '|' ||
        metadata_enabled::text || '|' ||
        COALESCE(metadata_profile, '') || '|' ||
        'bge-m3' || '|' ||
        chunk_max_chars::text || '|' ||
        COALESCE(chunk_overlap::text, '')
    ),
    updated_at = NOW()
WHERE embedding_model_id IN (
    'mxbai-embed-large',
    'mxbai-embed-large:latest',
    'hf.co/mixedbread-ai/mxbai-embed-large-v1:latest'
);

-- New projects inherit bge-m3 (EMB-1 winner via LiteLLM).
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
        'bge-m3',
        400,
        NULL,
        md5('CHUNK_LEVEL|false||bge-m3|400|'),
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (project_id) DO NOTHING;
    RETURN NEW;
END;
$$;
