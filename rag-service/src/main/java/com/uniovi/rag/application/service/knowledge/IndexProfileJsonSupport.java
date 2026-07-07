package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.llm.LlmProvider;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Shared JSON helpers for {@code knowledge_index_snapshot.index_profile_jsonb}. */
public final class IndexProfileJsonSupport {

    public static final String EMBEDDING_MODEL_ID_KEY = "embeddingModelId";
    public static final String EMBEDDING_PROVIDER_KEY = "embeddingProvider";

    private IndexProfileJsonSupport() {}

    public static Optional<String> readEmbeddingModelId(Map<String, Object> indexProfileJsonb) {
        if (indexProfileJsonb == null || indexProfileJsonb.isEmpty()) {
            return Optional.empty();
        }
        Object v = indexProfileJsonb.get(EMBEDDING_MODEL_ID_KEY);
        if (v == null) {
            return Optional.empty();
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? Optional.empty() : Optional.of(s);
    }

    /**
     * Reads embedding provider from snapshot profile JSON. Legacy snapshots without the field are treated as
     * {@link LlmProvider#OLLAMA_NATIVE} at compatibility-check time only.
     */
    public static Optional<LlmProvider> readEmbeddingProvider(Map<String, Object> indexProfileJsonb) {
        if (indexProfileJsonb == null || indexProfileJsonb.isEmpty()) {
            return Optional.empty();
        }
        Object v = indexProfileJsonb.get(EMBEDDING_PROVIDER_KEY);
        if (v == null) {
            return Optional.empty();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LlmProvider.valueOf(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Legacy indices built before provider tagging default to Ollama-native embeddings. */
    public static LlmProvider resolveEmbeddingProviderOrLegacyDefault(Map<String, Object> indexProfileJsonb) {
        return readEmbeddingProvider(indexProfileJsonb).orElse(LlmProvider.OLLAMA_NATIVE);
    }

    public static String normalizeEmbeddingKey(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(":latest")) {
            return normalized.substring(0, normalized.length() - ":latest".length());
        }
        return normalized;
    }

    /**
     * True when two embedding ids refer to the same logical model (exact normalized match or known LiteLLM alias
     * families used in thesis embedding campaigns).
     */
    public static boolean embeddingKeysEquivalent(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (normalizeEmbeddingKey(a).equals(normalizeEmbeddingKey(b))) {
            return true;
        }
        return mixedbreadMxbaiAliasFamily(a) && mixedbreadMxbaiAliasFamily(b);
    }

    /** {@code mxbai-embed-large} and {@code hf.co/mixedbread-ai/mxbai-embed-large-v1} are deployment aliases. */
    public static boolean mixedbreadMxbaiAliasFamily(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = normalizeEmbeddingKey(raw);
        return "mxbai-embed-large".equals(normalized) || normalized.contains("mixedbread-ai/mxbai-embed-large");
    }
}
