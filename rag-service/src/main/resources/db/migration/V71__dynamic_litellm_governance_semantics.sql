-- Dynamic LiteLLM governance semantics (no schema change):
-- allowed_model.in_allowlist=FALSE means explicitly blocked at runtime.
-- in_allowlist=TRUE or missing row does NOT gate runtime; catalog + blocklist govern access.
-- V70 demo seeds remain display defaults only.

COMMENT ON COLUMN allowed_model.in_allowlist IS
    'When FALSE, model is explicitly blocked from runtime selection. TRUE or absent row does not gate catalog-known models.';
