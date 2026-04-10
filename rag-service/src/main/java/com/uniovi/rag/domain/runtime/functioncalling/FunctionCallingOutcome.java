package com.uniovi.rag.domain.runtime.functioncalling;

/**
 * Terminal FC outcomes persisted on {@link com.uniovi.rag.domain.runtime.engine.ExecutionTrace}.
 */
public enum FunctionCallingOutcome {
    SUPPRESSED_BY_CLARIFICATION,
    SUPPRESSED_BY_DETERMINISTIC_TOOL,
    FC_BLOCKED_BY_DETERMINISTIC_TOOL_FAILURE,
    DISABLED_BY_CONFIG,
    SUPPRESSED_BY_AMBIGUITY,
    NOT_APPLICABLE,
    MODEL_DECLINED,
    INVALID_MODEL_OUTPUT,
    EXECUTED_SUCCESS,
    EXECUTED_FAILED_INFRA
}
