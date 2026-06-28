package com.uniovi.rag.interfaces.rest.dto;

import java.util.Map;

/**
 * Stable, display-ready Chat source DTO (REST layer).
 */
public record ChatSourceDto(
        String documentId,
        String projectDocumentId,
        String filename,
        String snippet,
        Double distance,
        String distanceLabel,
        Integer chunkIndex,
        String detectedDate,
        Map<String, Object> metadata
) {}

