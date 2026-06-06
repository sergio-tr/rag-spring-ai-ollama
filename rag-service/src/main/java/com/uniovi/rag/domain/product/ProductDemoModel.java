package com.uniovi.rag.domain.product;

import com.uniovi.rag.domain.AllowedModelType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

/**
 * Curated demo model ids exposed via the product model registry (read + pull); not the DB allowlist.
 */
public enum ProductDemoModel {
    GEMMA3_4B("gemma3:4b", AllowedModelType.LLM, -1),
    MISTRAL_7B("mistral:7b", AllowedModelType.LLM, -1),
    LLAMA3_1_8B("llama3.1:8b", AllowedModelType.LLM, -1),
    /** Ollama mxbai-embed-large outputs 1024-dimensional vectors. */
    MXBAI_EMBED_LARGE("mxbai-embed-large", AllowedModelType.EMBEDDING, 1024),
    /** Ollama nomic-embed-text outputs 768-dimensional vectors (incompatible with default 1024-wide pgvector store). */
    NOMIC_EMBED_TEXT("nomic-embed-text", AllowedModelType.EMBEDDING, 768),
    /** Ollama qwen3-embedding outputs 1024-dimensional vectors in this deployment. */
    QWEN3_EMBEDDING("qwen3-embedding", AllowedModelType.EMBEDDING, 1024);

    private final String modelId;
    private final AllowedModelType modelType;
    /** Documented Ollama output width; {@code -1} for non-embedding models. */
    private final int documentedOutputDimensions;

    ProductDemoModel(String modelId, AllowedModelType modelType, int documentedOutputDimensions) {
        this.modelId = modelId;
        this.modelType = modelType;
        this.documentedOutputDimensions = documentedOutputDimensions;
    }

    public String modelId() {
        return modelId;
    }

    public AllowedModelType modelType() {
        return modelType;
    }

    /**
     * Documented embedding width for demo target models (from Ollama model cards). Empty for LLM rows.
     */
    public OptionalInt documentedOutputDimensions() {
        if (modelType != AllowedModelType.EMBEDDING || documentedOutputDimensions <= 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(documentedOutputDimensions);
    }

    /**
     * Whether this embedding model can index/query against a pgvector store with the given fixed column width.
     * Does not probe Ollama; use {@link com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard} at runtime.
     */
    public boolean fitsStoreEmbeddingDimension(int storeEmbeddingDimension) {
        return documentedOutputDimensions().orElse(-1) == storeEmbeddingDimension;
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
