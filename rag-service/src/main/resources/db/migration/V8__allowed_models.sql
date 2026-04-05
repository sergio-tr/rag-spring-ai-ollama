-- LLM / embedding models exposed to the product (allowlist vs Ollama reality).

CREATE TABLE allowed_model (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL CHECK (type IN ('LLM', 'EMBEDDING')),
    in_allowlist BOOLEAN NOT NULL DEFAULT TRUE,
    installed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_allowed_model_name_type ON allowed_model (name, type);
