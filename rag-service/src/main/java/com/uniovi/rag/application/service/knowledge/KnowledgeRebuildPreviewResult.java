package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.KnowledgeBuildProjection;
import com.uniovi.rag.domain.knowledge.KnowledgeReindexDecision;

/** Read-only preview: projection + precomputed reindex decision. */
public record KnowledgeRebuildPreviewResult(
        KnowledgeBuildProjection projection, KnowledgeReindexDecision decision) {}
