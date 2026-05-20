package com.uniovi.rag.infrastructure.persistence.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test fixtures for {@code knowledge_index_snapshot} rows.
 *
 * <p>Integration tests must satisfy NOT NULL constraints introduced by schema migrations:
 * {@code index_profile_jsonb} and {@code index_profile_hash} must be populated.
 */
public final class KnowledgeSnapshotTestFixtures {

    private KnowledgeSnapshotTestFixtures() {}

    public static Map<String, Object> defaultIndexProfileJsonb() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("materializationStrategy", "CHUNK_LEVEL");
        m.put("supportsMetadata", false);
        m.put("embeddingModelId", "test-embedding");
        m.put("chunkMaxChars", 400);
        m.put("chunkOverlap", 50);
        m.put("metadataProfile", "NONE");
        return m;
    }

    public static String defaultIndexProfileHash() {
        return "test-profile-hash-chunk-no-metadata";
    }
}

