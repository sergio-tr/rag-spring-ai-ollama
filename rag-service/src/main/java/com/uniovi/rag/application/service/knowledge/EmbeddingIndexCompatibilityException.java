package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.llm.LlmProvider;
import java.util.LinkedHashMap;
import java.util.Map;

/** Structured ingestion/retrieval embedding compatibility failure with API-safe details. */
public class EmbeddingIndexCompatibilityException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public EmbeddingIndexCompatibilityException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details != null ? Map.copyOf(details) : Map.of();
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static EmbeddingIndexCompatibilityException projectIndexProfileMismatch(
            String activeProfileEmbeddingModel, String resolvedEmbeddingModel, String deploymentEmbeddingModel) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("activeProfileEmbeddingModel", activeProfileEmbeddingModel);
        details.put("resolvedEmbeddingModel", resolvedEmbeddingModel);
        if (deploymentEmbeddingModel != null && !deploymentEmbeddingModel.isBlank()) {
            details.put("deploymentEmbeddingModel", deploymentEmbeddingModel);
        }
        return new EmbeddingIndexCompatibilityException(
                IngestionEmbeddingReasonCodes.PROJECT_INDEX_PROFILE_MISMATCH,
                "Project index profile embedding model '"
                        + activeProfileEmbeddingModel
                        + "' does not match resolved ingestion model '"
                        + resolvedEmbeddingModel
                        + "'. Align the project index profile with the configured embedding catalog.",
                details);
    }

    public static EmbeddingIndexCompatibilityException embeddingModelAliasMismatch(
            String activeProfileEmbeddingModel, String resolvedEmbeddingModel) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("activeProfileEmbeddingModel", activeProfileEmbeddingModel);
        details.put("resolvedEmbeddingModel", resolvedEmbeddingModel);
        return new EmbeddingIndexCompatibilityException(
                IngestionEmbeddingReasonCodes.EMBEDDING_MODEL_ALIAS_MISMATCH,
                "Embedding model alias mismatch between active profile '"
                        + activeProfileEmbeddingModel
                        + "' and resolved ingestion model '"
                        + resolvedEmbeddingModel
                        + "'.",
                details);
    }

    public static EmbeddingIndexCompatibilityException noCompatibleVectorIndex(
            LlmProvider provider, String embeddingModel) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (provider != null) {
            details.put("embeddingProvider", provider.name());
        }
        details.put("resolvedEmbeddingModel", embeddingModel != null ? embeddingModel : "");
        return new EmbeddingIndexCompatibilityException(
                IngestionEmbeddingReasonCodes.NO_COMPATIBLE_VECTOR_INDEX,
                String.format(
                        EmbeddingIndexCompatibilityService.NO_COMPATIBLE_INDEX_MESSAGE,
                        provider,
                        embeddingModel),
                details);
    }

    public static EmbeddingIndexCompatibilityException reindexRequired(
            String activeProfileEmbeddingModel, String resolvedEmbeddingModel) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("activeProfileEmbeddingModel", activeProfileEmbeddingModel);
        details.put("resolvedEmbeddingModel", resolvedEmbeddingModel);
        return new EmbeddingIndexCompatibilityException(
                IngestionEmbeddingReasonCodes.REINDEX_REQUIRED,
                "Reindex is required before ingesting with embedding model '"
                        + resolvedEmbeddingModel
                        + "'.",
                details);
    }
}
