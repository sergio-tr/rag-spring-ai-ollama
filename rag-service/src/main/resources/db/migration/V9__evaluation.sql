-- Evaluation datasets, runs, and per-question results (classifier references dataset in V10).

CREATE TABLE evaluation_dataset (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id UUID REFERENCES users (id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    file_name VARCHAR(512),
    question_count INTEGER,
    sha256 VARCHAR(64),
    type VARCHAR(32) NOT NULL CHECK (type IN ('RAG', 'LLM_ONLY', 'CLASSIFIER')),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validated_at TIMESTAMPTZ
);

CREATE INDEX idx_evaluation_dataset_owner ON evaluation_dataset (owner_id);

CREATE TABLE evaluation_run (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name VARCHAR(512),
    dataset_id UUID NOT NULL REFERENCES evaluation_dataset (id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL CHECK (type IN ('LLM_ONLY', 'CLASSIFIER', 'RAG_FULL')),
    config_ids JSONB NOT NULL DEFAULT '[]',
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'ERROR')),
    progress INTEGER NOT NULL DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_evaluation_run_dataset ON evaluation_run (dataset_id);

CREATE TABLE evaluation_result (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id UUID NOT NULL REFERENCES evaluation_run (id) ON DELETE CASCADE,
    config_id UUID REFERENCES rag_configuration (id) ON DELETE SET NULL,
    config_snapshot JSONB,
    question_text TEXT,
    expected_answer TEXT,
    actual_answer TEXT,
    correctness INTEGER CHECK (correctness >= 1 AND correctness <= 5),
    sources JSONB,
    query_type VARCHAR(64),
    latency_ms BIGINT,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_evaluation_result_run ON evaluation_result (run_id);
