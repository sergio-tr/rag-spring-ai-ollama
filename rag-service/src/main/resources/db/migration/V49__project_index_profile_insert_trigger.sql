-- Ensure every project has a default index profile row at creation time.
-- This keeps project creation independent from application wiring (webmvc slices) and guarantees consistency.

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
        'mxbai-embed-large',
        400,
        NULL,
        md5('CHUNK_LEVEL|false||mxbai-embed-large|400|'),
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (project_id) DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_projects_ensure_index_profile ON projects;

CREATE TRIGGER trg_projects_ensure_index_profile
AFTER INSERT ON projects
FOR EACH ROW
EXECUTE FUNCTION ensure_project_index_profile_on_project_insert();

