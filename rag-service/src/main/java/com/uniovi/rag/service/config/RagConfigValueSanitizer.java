package com.uniovi.rag.service.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Filters RAG config maps to keys allowed at USER_DEFAULT / PROJECT level and in presets.
 */
public final class RagConfigValueSanitizer {

    public static final Set<String> ALLOWED_KEYS = Set.of(
            "expansionEnabled",
            "nerEnabled",
            "toolsEnabled",
            "metadataEnabled",
            "reasoningEnabled",
            "rankerEnabled",
            "postRetrievalEnabled",
            "functionCallingEnabled",
            "useRetrieval",
            "useAdvisor",
            "topK",
            "similarityThreshold",
            "llmModel",
            "embeddingModel",
            "classifierModelId",
            "reasoningStrategy",
            "naiveFullCorpusInPromptEnabled",
            "naiveFullCorpusMaxChars",
            "corpusGroundedDirectWorkflow");

    private RagConfigValueSanitizer() {
    }

    public static Map<String, Object> sanitize(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (ALLOWED_KEYS.contains(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }
}
