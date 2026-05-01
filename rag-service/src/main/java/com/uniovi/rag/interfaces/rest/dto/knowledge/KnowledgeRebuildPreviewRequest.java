package com.uniovi.rag.interfaces.rest.dto.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.domain.knowledge.CorpusScope;

import java.util.List;
import java.util.UUID;

/** Body for {@code POST .../knowledge/rebuild/preview}. */
public record KnowledgeRebuildPreviewRequest(
        CorpusScope corpusScope,
        UUID conversationId,
        UUID presetId,
        JsonNode runtimeOverride,
        List<String> touchedProfileTypes,
        String correlationId) {}
