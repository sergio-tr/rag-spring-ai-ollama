package com.uniovi.rag.domain.llm.catalog;

/** Role-level capabilities used for RAG chat model routing (distinct from catalog CHAT/EMBEDDING). */
public enum LlmModelRoleCapability {
    CHAT_PRIMARY,
    CHAT_SECONDARY,
    EMBEDDING,
    OCR,
    VISION,
    RERANKER
}
