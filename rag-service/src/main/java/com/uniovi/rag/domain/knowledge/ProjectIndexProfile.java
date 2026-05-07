package com.uniovi.rag.domain.knowledge;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Project-level index profile: defines index-time capabilities and constraints.
 */
public record ProjectIndexProfile(
        UUID projectId,
        MaterializationStrategy materializationStrategy,
        boolean metadataEnabled,
        String metadataProfile,
        String embeddingModelId,
        int chunkMaxChars,
        Integer chunkOverlap,
        String profileHash,
        Instant createdAt,
        Instant updatedAt
) {

    public Map<String, Object> toSnapshotJsonb() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("materializationStrategy", materializationStrategy != null ? materializationStrategy.name() : null);
        m.put("supportsMetadata", metadataEnabled);
        m.put("metadataProfile", metadataProfile);
        m.put("embeddingModelId", embeddingModelId);
        m.put("chunkMaxChars", chunkMaxChars);
        m.put("chunkOverlap", chunkOverlap);
        return m;
    }

    public static String computeProfileHash(
            MaterializationStrategy materializationStrategy,
            boolean metadataEnabled,
            String metadataProfile,
            String embeddingModelId,
            int chunkMaxChars,
            Integer chunkOverlap
    ) {
        // Use md5-compatible canonical string (matches migration) for portability.
        String canon =
                String.valueOf(materializationStrategy != null ? materializationStrategy.name() : "") +
                        "|" +
                        metadataEnabled +
                        "|" +
                        String.valueOf(metadataProfile != null ? metadataProfile : "") +
                        "|" +
                        String.valueOf(embeddingModelId != null ? embeddingModelId : "") +
                        "|" +
                        chunkMaxChars +
                        "|" +
                        String.valueOf(chunkOverlap != null ? chunkOverlap : "");
        return Md5Hex.md5Hex(canon);
    }

    public ProjectIndexProfile {
        Objects.requireNonNull(projectId, "projectId");
    }
}

