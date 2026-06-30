package com.uniovi.rag.domain.llm.catalog;

/** Context in which a catalog entry is being validated. */
public enum LlmModelUsageContext {
    SYSTEM_DEFAULT,
    USER_SELECTION,
    RAG_CHAT,
    RAG_EMBEDDING,
    ADMIN_VALIDATION
}
