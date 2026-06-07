package com.uniovi.rag.application.service.knowledge;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Shared JSON helpers for {@code knowledge_index_snapshot.index_profile_jsonb}. */
public final class IndexProfileJsonSupport {

    private IndexProfileJsonSupport() {}

    public static Optional<String> readEmbeddingModelId(Map<String, Object> indexProfileJsonb) {
        if (indexProfileJsonb == null || indexProfileJsonb.isEmpty()) {
            return Optional.empty();
        }
        Object v = indexProfileJsonb.get("embeddingModelId");
        if (v == null) {
            return Optional.empty();
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? Optional.empty() : Optional.of(s);
    }

    public static String normalizeEmbeddingKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
