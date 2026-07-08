package com.uniovi.rag.application.service.runtime.optimization;

/** Thrown when an optional secondary LLM call is skipped because the per-turn budget is exhausted. */
public final class OptionalLlmCallBudgetSkippedException extends RuntimeException {

    private final String operation;

    public OptionalLlmCallBudgetSkippedException(String operation) {
        super("optional_llm_call_skipped:" + operation);
        this.operation = operation;
    }

    public String operation() {
        return operation;
    }
}
