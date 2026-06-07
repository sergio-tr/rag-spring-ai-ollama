package com.uniovi.rag.application.service.runtime.config;

import java.util.Map;

/**
 * Minimal normalized capabilities derived from an active index snapshot profile JSON.
 *
 * <p>This is intentionally tolerant: unknown/missing fields yield nulls.
 */
public record IndexSnapshotCapabilities(
        String materializationStrategy,
        Boolean supportsMetadata,
        String embeddingModelId,
        Integer chunkMaxChars,
        Integer chunkOverlap
) {
    public static IndexSnapshotCapabilities fromIndexProfile(Map<String, Object> profile) {
        if (profile == null || profile.isEmpty()) {
            return new IndexSnapshotCapabilities(null, null, null, null, null);
        }
        return new IndexSnapshotCapabilities(
                stringOrNull(profile.get("materializationStrategy")),
                boolOrNull(profile.get("supportsMetadata")),
                stringOrNull(profile.get("embeddingModelId")),
                intOrNull(profile.get("chunkMaxChars")),
                intOrNull(profile.get("chunkOverlap")));
    }

    private static String stringOrNull(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private static Boolean boolOrNull(Object o) {
        return o instanceof Boolean b ? b : null;
    }

    private static Integer intOrNull(Object o) {
        if (o instanceof Integer i) return i;
        if (o instanceof Long l) return Math.toIntExact(l);
        if (o instanceof Number n) return n.intValue();
        return null;
    }
}

