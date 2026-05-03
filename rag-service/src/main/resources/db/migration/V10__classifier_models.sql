-- Trained classifier artifacts (depends on evaluation_dataset).

CREATE TABLE classifier_model (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id UUID REFERENCES users (id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    dataset_id UUID REFERENCES evaluation_dataset (id) ON DELETE SET NULL,
    dataset_sha VARCHAR(64),
    hyperparams JSONB NOT NULL DEFAULT '{}',
    accuracy DOUBLE PRECISION,
    f1_macro DOUBLE PRECISION,
    artifact_path TEXT,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    passes_gate BOOLEAN NOT NULL DEFAULT FALSE,
    trained_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL CHECK (status IN ('TRAINING', 'READY', 'ERROR'))
);

CREATE INDEX idx_classifier_model_owner ON classifier_model (owner_id);
