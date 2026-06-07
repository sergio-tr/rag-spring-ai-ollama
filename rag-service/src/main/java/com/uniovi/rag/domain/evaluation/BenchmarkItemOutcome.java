package com.uniovi.rag.domain.evaluation;

/** Per benchmark row outcome persisted under metrics payload / structured results. */
public enum BenchmarkItemOutcome {
    EXECUTED,
    FAILED,
    SKIPPED,
    NOT_SUPPORTED,
    /** Requested Ollama model not installed / API unreachable for model resolution (item skipped with metrics). */
    MODEL_NOT_AVAILABLE
}
