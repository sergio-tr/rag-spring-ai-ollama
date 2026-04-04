package com.uniovi.rag.domain;

/**
 * Long-running tasks executed outside the servlet thread (see {@code async_task}).
 */
public enum AsyncTaskType {
    EVAL_LLM,
    EVAL_RAG,
    CLASSIFIER_TRAIN,
    CLASSIFIER_EVAL,
    OLLAMA_PULL,
    /** One in-flight job per user; supersedes prior chat jobs when a new message is queued. */
    CHAT_MESSAGE
}
