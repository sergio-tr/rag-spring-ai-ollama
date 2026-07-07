-- Thesis evaluation Phase 1: re-enable bge-m3 embedding governance and align thesis allowlist rows.
-- V61 demoted bge-m3 for Lab defaults; thesis 4-model embedding matrix requires bge-m3 (LiteLLM untagged id).
-- Do not edit V59/V61.

UPDATE allowed_model
SET in_allowlist = TRUE,
    available = FALSE
WHERE lower(name) = 'bge-m3'
  AND type = 'EMBEDDING';

INSERT INTO allowed_model (name, type, in_allowlist, available, display_name)
VALUES
    ('hf.co/mixedbread-ai/mxbai-embed-large-v1:latest', 'EMBEDDING', TRUE, FALSE, 'MxBai Embed Large (HF)'),
    ('mxbai-embed-large', 'EMBEDDING', TRUE, FALSE, 'MxBai Embed Large'),
    ('snowflake-arctic-embed2', 'EMBEDDING', TRUE, FALSE, 'Snowflake Arctic Embed 2'),
    ('deepseek-r1:1.5b', 'LLM', TRUE, FALSE, 'DeepSeek R1 1.5B')
ON CONFLICT (name, type) DO UPDATE
SET in_allowlist = EXCLUDED.in_allowlist,
    display_name = COALESCE(allowed_model.display_name, EXCLUDED.display_name);
