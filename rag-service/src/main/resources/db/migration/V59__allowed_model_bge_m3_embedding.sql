-- Ensure bge-m3 is typed as EMBEDDING in the DB allowlist (not trapped as LLM).

DELETE FROM allowed_model
WHERE lower(name) IN ('bge-m3', 'bge-m3:latest')
  AND type = 'LLM';

INSERT INTO allowed_model (name, type, in_allowlist, available, display_name)
SELECT 'bge-m3', 'EMBEDDING', TRUE, FALSE, 'BGE-M3'
WHERE NOT EXISTS (
    SELECT 1 FROM allowed_model WHERE lower(name) = 'bge-m3' AND type = 'EMBEDDING'
);
