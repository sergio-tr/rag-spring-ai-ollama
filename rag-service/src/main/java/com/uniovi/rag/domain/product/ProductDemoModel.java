package com.uniovi.rag.domain.product;

import com.uniovi.rag.domain.AllowedModelType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Curated demo / thesis model ids exposed via the product model registry (read + pull); not the DB allowlist.
 */
public enum ProductDemoModel {
    GEMMA3_4B("gemma3:4b", AllowedModelType.LLM),
    MISTRAL_7B("mistral:7b", AllowedModelType.LLM),
    LLAMA3_1_8B("llama3.1:8b", AllowedModelType.LLM),
    MXBAI_EMBED_LARGE("mxbai-embed-large", AllowedModelType.EMBEDDING),
    NOMIC_EMBED_TEXT("nomic-embed-text", AllowedModelType.EMBEDDING),
    BGE_M3("bge-m3", AllowedModelType.EMBEDDING);

    private final String modelId;
    private final AllowedModelType modelType;

    ProductDemoModel(String modelId, AllowedModelType modelType) {
        this.modelId = modelId;
        this.modelType = modelType;
    }

    public String modelId() {
        return modelId;
    }

    public AllowedModelType modelType() {
        return modelType;
    }

    public static List<ProductDemoModel> llmModels() {
        return Arrays.stream(values()).filter(m -> m.modelType == AllowedModelType.LLM).toList();
    }

    public static List<ProductDemoModel> embeddingModels() {
        return Arrays.stream(values()).filter(m -> m.modelType == AllowedModelType.EMBEDDING).toList();
    }

    public static Stream<ProductDemoModel> all() {
        return Arrays.stream(values());
    }

    /**
     * Resolves a user-supplied id against the curated registry (trimmed, case-insensitive for the known ids).
     */
    public static Optional<ProductDemoModel> resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String t = raw.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        for (ProductDemoModel m : values()) {
            if (m.modelId.equalsIgnoreCase(t) || m.modelId.toLowerCase(Locale.ROOT).equals(lower)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}
