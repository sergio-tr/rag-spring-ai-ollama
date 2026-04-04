package com.uniovi.rag.domain.evaluation;

/**
 * Canonical benchmark kinds. Each maps to a dedicated use case / handler path.
 */
public enum BenchmarkKind {
    LLM_JUDGE_QA,
    EMBEDDING_RETRIEVAL,
    RAG_PRESET_END_TO_END,
    CLASSIFIER_METRICS
}
