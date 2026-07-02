package com.uniovi.rag.application.service.embedding;

import com.uniovi.rag.application.service.llm.catalog.LlmCatalogApiService;
import com.uniovi.rag.domain.embedding.EmbeddingModelCapabilities;
import com.uniovi.rag.domain.llm.LlmProvider;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import org.springframework.stereotype.Component;

/** Derives embedding capability metadata from provider and model name heuristics. */
@Component
public class EmbeddingCapabilityResolver {

    public EmbeddingModelCapabilities resolve(LlmProvider provider, String modelName) {
        if (provider == LlmProvider.OLLAMA_NATIVE) {
            return ollamaCapabilities(modelName);
        }
        return openAiCompatibleCapabilities(modelName);
    }

    private static EmbeddingModelCapabilities ollamaCapabilities(String modelName) {
        OptionalInt dims = LlmCatalogApiService.resolveEmbeddingDimensions(modelName);
        return new EmbeddingModelCapabilities(
                false,
                List.of(),
                false,
                dims.isPresent() ? dims.getAsInt() : null,
                estimateMaxInputTokens(modelName),
                false,
                false);
    }

    private static EmbeddingModelCapabilities openAiCompatibleCapabilities(String modelName) {
        String lower = modelName == null ? "" : modelName.toLowerCase(Locale.ROOT);
        OptionalInt dims = LlmCatalogApiService.resolveEmbeddingDimensions(modelName);
        boolean supportsDimensions = supportsReducedDimensions(lower);
        Integer defaultDimensions = dims.isPresent() ? dims.getAsInt() : null;
        return new EmbeddingModelCapabilities(
                true,
                List.of("float", "base64"),
                supportsDimensions,
                defaultDimensions,
                estimateMaxInputTokens(modelName),
                lower.contains("bge-m3"),
                lower.contains("bge-m3") || lower.contains("nomic-embed"));
    }

    private static boolean supportsReducedDimensions(String lowerModelName) {
        if (lowerModelName.contains("text-embedding-3")) {
            return true;
        }
        if (lowerModelName.contains("bge-m3")) {
            return true;
        }
        if (lowerModelName.contains("snowflake-arctic-embed")) {
            return true;
        }
        return false;
    }

    private static Integer estimateMaxInputTokens(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.contains("bge-m3")) {
            return 8192;
        }
        if (lower.contains("mxbai-embed") || lower.contains("mixedbread")) {
            return 512;
        }
        if (lower.contains("nomic-embed")) {
            return 8192;
        }
        if (lower.contains("qwen3-embedding") || lower.contains("qwen3-embed")) {
            return 8192;
        }
        return 8192;
    }
}
