-- LiteLLM demo chat models: align governance allowlist with configured LiteLLM defaults.
-- Does not open the full properties catalog; only adds demo-relevant chat models.

INSERT INTO allowed_model (name, type, in_allowlist, available, display_name)
VALUES
    ('gpt-oss:20b', 'LLM', TRUE, FALSE, 'GPT OSS 20B'),
    ('qwen3.6:35b', 'LLM', TRUE, FALSE, 'Qwen3.6 35B')
ON CONFLICT (name, type) DO UPDATE
SET in_allowlist = EXCLUDED.in_allowlist,
    display_name = COALESCE(allowed_model.display_name, EXCLUDED.display_name);
