-- Background jobs for lab evaluations, classifier ops, and admin Ollama pull (non-blocking HTTP).

CREATE TABLE async_task (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    progress_text TEXT,
    request_payload JSONB,
    result_json JSONB,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_async_task_user_created ON async_task (user_id, created_at DESC);
CREATE INDEX idx_async_task_status ON async_task (status);
