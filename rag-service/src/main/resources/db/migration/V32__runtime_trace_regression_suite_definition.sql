-- P33: persisted regression suite definitions (metadata + ordered entries); execution remains P30.

CREATE TABLE runtime_trace_regression_suite_definition (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(256) NOT NULL,
    description VARCHAR(2048),
    schema_version SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_runtime_trace_regression_suite_definition_user_name UNIQUE (user_id, name)
);

CREATE TABLE runtime_trace_regression_suite_definition_entry (
    id UUID PRIMARY KEY,
    definition_id UUID NOT NULL REFERENCES runtime_trace_regression_suite_definition (id) ON DELETE CASCADE,
    position SMALLINT NOT NULL CHECK (position >= 0 AND position <= 19),
    entry_kind VARCHAR(32) NOT NULL CHECK (entry_kind IN ('BY_TRACE_IDS', 'BY_CONVERSATION')),
    conversation_id UUID,
    created_at_from TIMESTAMPTZ,
    created_at_to TIMESTAMPTZ,
    workflow_name VARCHAR(256),
    CONSTRAINT uq_runtime_trace_regression_suite_definition_entry_def_pos UNIQUE (definition_id, position),
    CONSTRAINT chk_rt_regr_suite_entry_by_trace_ids CHECK (
        entry_kind <> 'BY_TRACE_IDS'
            OR (
                conversation_id IS NULL
                AND created_at_from IS NULL
                AND created_at_to IS NULL
                AND workflow_name IS NULL
            )
    ),
    CONSTRAINT chk_rt_regr_suite_entry_by_conversation CHECK (
        entry_kind <> 'BY_CONVERSATION' OR conversation_id IS NOT NULL
    )
);

CREATE TABLE runtime_trace_regression_suite_definition_entry_trace (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL REFERENCES runtime_trace_regression_suite_definition_entry (id) ON DELETE CASCADE,
    position SMALLINT NOT NULL CHECK (position >= 0 AND position <= 49),
    trace_id UUID NOT NULL,
    CONSTRAINT uq_rt_regr_suite_entry_trace_pos UNIQUE (entry_id, position),
    CONSTRAINT uq_rt_regr_suite_entry_trace_id UNIQUE (entry_id, trace_id)
);

CREATE INDEX idx_rt_regr_suite_def_user ON runtime_trace_regression_suite_definition (user_id);
CREATE INDEX idx_rt_regr_suite_entry_def ON runtime_trace_regression_suite_definition_entry (definition_id);
CREATE INDEX idx_rt_regr_suite_trace_entry ON runtime_trace_regression_suite_definition_entry_trace (entry_id);
