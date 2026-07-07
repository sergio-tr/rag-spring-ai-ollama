-- Classifier registered model semantics documentation (20250701 gate).
-- Application-level validation enforces name/inference-tag rules; no destructive data changes.

COMMENT ON COLUMN classifier_model.name IS
    'Human unique application identifier for the trained classifier (display name at training time). Not the inference tag.';

COMMENT ON COLUMN classifier_model.artifact_path IS
    'Classifier-service inference tag sent as classifierModelId at runtime. May equal the system tag (e.g. default) for catalog sync rows only.';
