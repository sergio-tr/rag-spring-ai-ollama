package com.uniovi.rag.domain.runtime.engine;

/**
 * Stable workflow identifiers aligned with {@code ExecutionWorkflow} implementation class names.
 */
public enum WorkflowId {
    DIRECT_LLM,
    FULL_CORPUS,
    DOCUMENT_DENSE_RAG,
    CHUNK_DENSE_RAG,
    CHUNK_DENSE_METADATA
}
