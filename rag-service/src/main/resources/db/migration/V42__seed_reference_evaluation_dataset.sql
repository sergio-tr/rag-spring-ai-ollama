-- Seeded SYSTEM dataset so canonical benchmarks can reference the packaged workbook by stable UUID
-- (matches Lab list synthetic id ExperimentalDatasetLabService.REFERENCE_DATASET_LIST_ENTRY_ID).

INSERT INTO evaluation_dataset (
    id,
    owner_id,
    name,
    file_name,
    question_count,
    sha256,
    type,
    uploaded_at,
    validated_at,
    dataset_scope,
    storage_uri,
    byte_size,
    mime_type,
    schema_version,
    description,
    experimental_kind,
    validation_status
) VALUES (
    '00000000-0000-7000-8000-000000000001',
    NULL,
    'Packaged reference workbook',
    'rag_experiment_datasets_and_protocols.xlsx',
    NULL,
    NULL,
    'RAG',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'SYSTEM_DATASET',
    'classpath:evaluation/rag_experiment_datasets_and_protocols.xlsx',
    NULL,
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'experimental-workbook-v1',
    'Internal classpath bundle (seeded for canonical benchmarks; ADMIN-only scope).',
    'REFERENCE_BUNDLE',
    'VALID'
)
ON CONFLICT (id) DO NOTHING;
