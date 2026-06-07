-- Phase 3: Lab experimental workbook uploads (distinct persisted kind + validation snapshot).
ALTER TABLE evaluation_dataset
    ADD COLUMN description TEXT;

ALTER TABLE evaluation_dataset
    ADD COLUMN experimental_kind VARCHAR(64);

ALTER TABLE evaluation_dataset
    ADD COLUMN validation_report_json JSONB;

ALTER TABLE evaluation_dataset
    ADD COLUMN validation_status VARCHAR(16);

COMMENT ON COLUMN evaluation_dataset.experimental_kind IS 'ExperimentalDatasetType name (LLM_MODEL_BASELINE, …); coarse type stays in type column.';
COMMENT ON COLUMN evaluation_dataset.validation_report_json IS 'Last validation issues snapshot (structured JSON).';
COMMENT ON COLUMN evaluation_dataset.validation_status IS 'VALID after successful upload; invalid uploads are not persisted.';
