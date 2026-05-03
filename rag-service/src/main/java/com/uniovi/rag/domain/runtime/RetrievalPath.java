package com.uniovi.rag.domain.runtime;

/**
 * Resolved retrieval mode for the RAG kernel. Selection is centralized in {@link RetrievalPolicyResolver}.
 */
public enum RetrievalPath {
    /** No vector retrieval; LLM-only answer when useRetrieval is false. */
    NO_RETRIEVAL_LLM,
    /** Naive JDBC concatenation in prompt when enabled; may skip advisor/vector similarity. */
    NAIVE_PROMPT_FIRST,
    /** Spring AI {@code QuestionAnswerAdvisor} fast path (vector similarity in advisor). */
    ADVISOR,
    /** Manual {@link com.uniovi.rag.service.retriever.ContextRetriever} + optional post-retrieval. */
    MANUAL_ONLY
}
