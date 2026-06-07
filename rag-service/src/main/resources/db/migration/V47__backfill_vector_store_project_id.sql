-- Backfill vector_store.project_id (column) from metadata->>'projectId'.
--
-- Root cause (Phase R0):
-- Spring AI PgVectorStore writes `metadata.projectId` but does not populate our optional `project_id` column.
-- Several retrieval paths (naive full corpus, scoped filters, future SQL optimizations) rely on `project_id`.
-- When NULL, project-scoped retrieval returns zero candidates even though chunks exist.
--
-- This migration repairs existing rows without modifying metadata.
UPDATE vector_store
SET project_id = (metadata->>'projectId')::uuid,
    updated_at = CURRENT_TIMESTAMP
WHERE project_id IS NULL
  AND metadata ? 'projectId'
  AND (metadata->>'projectId') ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
  AND EXISTS (
      SELECT 1
      FROM projects p
      WHERE p.id = (metadata->>'projectId')::uuid
  );

