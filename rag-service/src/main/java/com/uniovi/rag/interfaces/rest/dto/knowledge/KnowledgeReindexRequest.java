package com.uniovi.rag.interfaces.rest.dto.knowledge;

import com.uniovi.rag.domain.knowledge.CorpusScope;

import java.util.UUID;

/** Query parameters for {@code POST .../knowledge/reindex}. */
public record KnowledgeReindexRequest(CorpusScope corpusScope, UUID conversationId) {}
