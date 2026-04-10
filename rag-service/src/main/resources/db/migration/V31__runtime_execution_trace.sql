-- Canonical persisted trace artifact for orchestrated runtime turns (P15).
CREATE TABLE IF NOT EXISTS runtime_execution_trace (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    conversation_id UUID NULL REFERENCES conversations (id) ON DELETE SET NULL,
    message_id UUID NULL REFERENCES messages (id) ON DELETE SET NULL,

    correlation_id VARCHAR(128) NOT NULL,
    resolved_config_snapshot_id UUID NULL REFERENCES resolved_config_snapshot (id) ON DELETE SET NULL,
    config_hash TEXT NULL,

    workflow_name TEXT NOT NULL,

    memory_attempted BOOLEAN NOT NULL DEFAULT FALSE,
    memory_outcome TEXT NOT NULL DEFAULT '',

    routing_attempted BOOLEAN NOT NULL DEFAULT FALSE,
    routing_outcome TEXT NOT NULL DEFAULT '',
    routing_route_kind TEXT NOT NULL DEFAULT '',
    routing_fallback_applied BOOLEAN NOT NULL DEFAULT FALSE,
    routing_workflow_selector_invoked BOOLEAN NOT NULL DEFAULT FALSE,

    deterministic_tool_outcome TEXT NOT NULL DEFAULT '',
    function_calling_outcome TEXT NOT NULL DEFAULT '',
    advisor_outcome TEXT NOT NULL DEFAULT '',

    judge_attempted BOOLEAN NOT NULL DEFAULT FALSE,
    judge_candidate_source TEXT NOT NULL DEFAULT '',
    judge_final_outcome TEXT NOT NULL DEFAULT '',
    judge_final_answer_from_retry BOOLEAN NOT NULL DEFAULT FALSE,

    clarification_outcome TEXT NOT NULL DEFAULT '',

    schema_version INT NOT NULL,
    execution_trace_jsonb JSONB NOT NULL,
    stages_jsonb JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_runtime_execution_trace_conversation
    ON runtime_execution_trace (conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_runtime_execution_trace_message
    ON runtime_execution_trace (message_id);
CREATE INDEX IF NOT EXISTS idx_runtime_execution_trace_config_snapshot
    ON runtime_execution_trace (resolved_config_snapshot_id);
CREATE INDEX IF NOT EXISTS idx_runtime_execution_trace_correlation
    ON runtime_execution_trace (correlation_id);

