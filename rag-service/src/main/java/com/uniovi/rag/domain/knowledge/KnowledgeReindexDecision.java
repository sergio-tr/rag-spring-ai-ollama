package com.uniovi.rag.domain.knowledge;

/**
 * Deterministic reindex outcome from {@link com.uniovi.rag.application.service.knowledge.KnowledgeConfigurationIntegrationService}.
 */
public record KnowledgeReindexDecision(KnowledgeReindexKind kind) {}
