package com.uniovi.rag.application.result.chat;

import java.util.Map;

/**
 * Internal, runtime/application source model (not a REST DTO).
 */
public record ChatSource(
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

