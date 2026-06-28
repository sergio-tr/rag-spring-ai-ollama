-- Lab evaluation corpus: document sets for RAG/embedding benchmarks independent of active project navigation.

CREATE TABLE evaluation_corpus (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    source_type VARCHAR(32) NOT NULL CHECK (source_type IN ('UPLOADED', 'FROM_PROJECT', 'MIXED')),
    index_project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_evaluation_corpus_owner_id ON evaluation_corpus (owner_id);

CREATE TABLE evaluation_corpus_document (
    corpus_id UUID NOT NULL REFERENCES evaluation_corpus (id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES project_documents (id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (corpus_id, document_id)
);

CREATE INDEX idx_evaluation_corpus_document_document_id ON evaluation_corpus_document (document_id);

ALTER TABLE evaluation_run
    ADD COLUMN IF NOT EXISTS evaluation_corpus_id UUID REFERENCES evaluation_corpus (id) ON DELETE SET NULL;

CREATE INDEX idx_evaluation_run_evaluation_corpus_id ON evaluation_run (evaluation_corpus_id);
