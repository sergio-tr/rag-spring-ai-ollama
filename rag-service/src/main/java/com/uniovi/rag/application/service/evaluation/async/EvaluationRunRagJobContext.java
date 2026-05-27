package com.uniovi.rag.application.service.evaluation.async;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable async RAG job setup context. No JPA entities or Hibernate proxies.
 */
public record EvaluationRunRagJobContext(
        UUID runId,
        UUID userId,
        UUID datasetId,
        String datasetExperimentalKind,
        UUID corpusId,
        String knowledgeBaseName,
        UUID projectId,
        boolean corpusBootstrapEnabled,
        boolean autoReindexEnabled,
        Map<String, Object> aggregatesJson,
        List<String> requestedPresetCodes) {

    public UUID knowledgeBaseId() {
        return corpusId;
    }

    public boolean hasCorpus() {
        return corpusId != null;
    }

    public boolean hasProject() {
        return projectId != null;
    }
}
