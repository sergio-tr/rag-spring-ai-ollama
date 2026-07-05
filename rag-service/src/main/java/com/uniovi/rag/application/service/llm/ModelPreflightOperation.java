package com.uniovi.rag.application.service.llm;

/** Stable operation labels for model preflight errors and logs. */
public enum ModelPreflightOperation {
    INDEXING_EMBEDDING("indexing-embedding"),
    CHAT("chat"),
    BENCHMARK_LLM("benchmark-llm"),
    BENCHMARK_EMBEDDING("benchmark-embedding"),
    BENCHMARK_JUDGE("benchmark-judge"),
    BENCHMARK_RAG("benchmark-rag");

    private final String wireName;

    ModelPreflightOperation(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
