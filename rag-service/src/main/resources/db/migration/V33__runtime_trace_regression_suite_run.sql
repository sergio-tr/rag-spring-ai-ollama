-- P41: durable regression suite run snapshots (header + per-entry scalars only).

CREATE TABLE runtime_trace_regression_suite_run (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL CHECK (source_type IN ('AD_HOC', 'SAVED_DEFINITION')),
    definition_id UUID,
    suite_outcome VARCHAR(64) NOT NULL CHECK (
        suite_outcome IN (
            'NOT_ATTEMPTED',
            'EMPTY_SUITE',
            'COMPLETED_ALL_BATCH_RETURNS',
            'COMPLETED_WITH_ENTRY_EXECUTION_FAILURES'
        )
    ),
    requested_entry_count INTEGER NOT NULL CHECK (requested_entry_count >= 0 AND requested_entry_count <= 20),
    processed_entry_count INTEGER NOT NULL CHECK (processed_entry_count >= 0 AND processed_entry_count <= 20),
    batch_returned_count INTEGER NOT NULL CHECK (batch_returned_count >= 0),
    execution_failed_count INTEGER NOT NULL CHECK (execution_failed_count >= 0),
    batch_not_attempted_subcount INTEGER NOT NULL CHECK (batch_not_attempted_subcount >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_rt_regr_suite_run_source_def CHECK (
        (source_type = 'AD_HOC' AND definition_id IS NULL)
        OR (source_type = 'SAVED_DEFINITION' AND definition_id IS NOT NULL)
    ),
    CONSTRAINT chk_rt_regr_suite_run_req_eq_batch_exec CHECK (
        requested_entry_count = batch_returned_count + execution_failed_count
    ),
    CONSTRAINT chk_rt_regr_suite_run_proc_eq_req CHECK (processed_entry_count = requested_entry_count),
    CONSTRAINT chk_rt_regr_suite_run_not_attempted_le_batch CHECK (
        batch_not_attempted_subcount <= batch_returned_count
    )
);

CREATE INDEX idx_rt_regr_suite_run_user_created ON runtime_trace_regression_suite_run (user_id, created_at DESC, id ASC);

CREATE TABLE runtime_trace_regression_suite_run_entry (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES runtime_trace_regression_suite_run (id) ON DELETE CASCADE,
    entry_order SMALLINT NOT NULL CHECK (entry_order >= 0 AND entry_order <= 19),
    entry_kind VARCHAR(32) NOT NULL CHECK (entry_kind IN ('BY_TRACE_IDS', 'BY_CONVERSATION')),
    selector_echo VARCHAR(256) NOT NULL,
    execution_status VARCHAR(32) NOT NULL CHECK (execution_status IN ('BATCH_RETURNED', 'EXECUTION_FAILED')),
    batch_outcome VARCHAR(64),
    requested_count INTEGER,
    selected_count INTEGER,
    processed_count INTEGER,
    failure_kind VARCHAR(32),
    failure_detail VARCHAR(1024),
    CONSTRAINT uq_rt_regr_suite_run_entry_run_order UNIQUE (run_id, entry_order),
    CONSTRAINT chk_rt_regr_suite_run_entry_batch_outcome CHECK (
        batch_outcome IS NULL
        OR batch_outcome IN (
            'NOT_ATTEMPTED',
            'EMPTY_SELECTION',
            'COMPLETED_ALL_EXACT_MATCH',
            'COMPLETED_WITH_COMPATIBLE_MISMATCHES_ONLY',
            'COMPLETED_WITH_STRUCTURAL_MISMATCHES',
            'COMPLETED_WITH_UNSUPPORTED_ITEMS',
            'COMPLETED_WITH_FAILED_SAFE_ITEMS',
            'COMPLETED_MIXED'
        )
    ),
    CONSTRAINT chk_rt_regr_suite_run_entry_failure_kind CHECK (
        failure_kind IS NULL
        OR failure_kind IN ('NOT_FOUND', 'ILLEGAL_ARGUMENT', 'UNEXPECTED')
    ),
    CONSTRAINT chk_rt_regr_suite_run_entry_req_nonneg CHECK (requested_count IS NULL OR requested_count >= 0),
    CONSTRAINT chk_rt_regr_suite_run_entry_sel_nonneg CHECK (selected_count IS NULL OR selected_count >= 0),
    CONSTRAINT chk_rt_regr_suite_run_entry_proc_nonneg CHECK (processed_count IS NULL OR processed_count >= 0),
    CONSTRAINT chk_rt_regr_suite_run_entry_row_shape CHECK (
        (
            execution_status = 'BATCH_RETURNED'
            AND failure_kind IS NULL
            AND failure_detail IS NULL
            AND batch_outcome IS NOT NULL
            AND requested_count IS NOT NULL
            AND selected_count IS NOT NULL
            AND processed_count IS NOT NULL
        )
        OR (
            execution_status = 'EXECUTION_FAILED'
            AND batch_outcome IS NULL
            AND requested_count IS NULL
            AND selected_count IS NULL
            AND processed_count IS NULL
            AND failure_kind IS NOT NULL
        )
    )
);

CREATE INDEX idx_rt_regr_suite_run_entry_run ON runtime_trace_regression_suite_run_entry (run_id);
