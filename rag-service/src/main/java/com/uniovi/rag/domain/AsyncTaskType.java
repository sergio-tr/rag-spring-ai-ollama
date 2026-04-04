package com.uniovi.rag.domain;

/**
 * Long-running tasks executed outside the servlet thread (see {@code async_task}).
 */
public enum AsyncTaskType {
    EVAL_LLM,
    EVAL_RAG,
    /** Retrieval-only vector benchmark ({@link com.uniovi.rag.domain.evaluation.BenchmarkKind#EMBEDDING_RETRIEVAL}). */
    EVAL_EMBEDDING_RETRIEVAL,
    CLASSIFIER_TRAIN,
    CLASSIFIER_EVAL,
    OLLAMA_PULL,
    /** One in-flight job per user; supersedes prior chat jobs when a new message is queued. */
    CHAT_MESSAGE
}
