package com.uniovi.rag.application.service.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.indexing.ReindexImpactLevel;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.knowledge.KnowledgeBuildProjection;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single owner of {@link KnowledgeBuildProjection}.
 */
@Component
public class KnowledgeBuildProjectionMapper {

    public static final int PROJECTION_VERSION = 1;
    public static final String PAYLOAD_KEY = "knowledgeBuildProjection";
    public static final String INTEGRITY_KEY = "integritySha256";

    /** Documented defaults when not present on {@link ResolvedRuntimeConfig} / {@link RagConfig}. */
    public static final int DEFAULT_CHUNK_MAX_CHARS = 400;

    public static final int DEFAULT_CHUNK_OVERLAP = 0;
    public static final MaterializationStrategy DEFAULT_MATERIALIZATION = MaterializationStrategy.CHUNK_LEVEL;

    private static final TypeReference<Map<String, Object>> MAP_STRING_OBJECT = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public KnowledgeBuildProjectionMapper(ObjectMapper objectMapper) {
        this.objectMapper =
                objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public KnowledgeBuildProjection fromResolvedRuntimeConfig(ResolvedRuntimeConfig resolved) {
        RagConfig rag = resolved.toRagConfig();
        ReindexImpact impact = resolved.reindexImpact() != null ? resolved.reindexImpact() : ReindexImpact.none();
        MaterializationStrategy strategy =
                rag.materializationStrategy() != null ? rag.materializationStrategy() : DEFAULT_MATERIALIZATION;
        int chunkMax = DEFAULT_CHUNK_MAX_CHARS;
        int overlap = DEFAULT_CHUNK_OVERLAP;
        String embedding = rag.embeddingModel() != null ? rag.embeddingModel() : "";
        boolean meta = rag.metadataEnabled();
        Map<String, Object> persist = buildPersistenceMap(strategy, chunkMax, overlap, embedding, meta, impact);
        String integrity = sha256HexUtf8(canonicalJson(persist));
        persist.put(INTEGRITY_KEY, integrity);
        return new KnowledgeBuildProjection(
                PROJECTION_VERSION,
                strategy,
                chunkMax,
                overlap,
                embedding,
                meta,
                impact,
                null,
                integrity);
    }

    public KnowledgeBuildProjection fromResolvedRuntimeConfigAndSnapshotIds(
            ResolvedRuntimeConfig resolved,
            UUID resolvedConfigSnapshotId,
            String persistedConfigHash) {
        KnowledgeBuildProjection base = fromResolvedRuntimeConfig(resolved);
        return new KnowledgeBuildProjection(
                base.projectionVersion(),
                base.materializationStrategy(),
                base.chunkMaxChars(),
                base.chunkOverlap(),
                base.embeddingModelId(),
                base.metadataExtractionEnabled(),
                base.reindexImpact(),
                resolvedConfigSnapshotId,
                persistedConfigHash != null ? persistedConfigHash : "");
    }

    /**
     * Nested map merged under {@value #PAYLOAD_KEY} in {@code resolved_config_snapshot.payload_jsonb}; includes
     * {@value #INTEGRITY_KEY}.
     */
    public Map<String, Object> toNestedPayloadMap(KnowledgeBuildProjection projection) {
        Map<String, Object> persist =
                buildPersistenceMap(
                        projection.materializationStrategy(),
                        projection.chunkMaxChars(),
                        projection.chunkOverlap(),
                        projection.embeddingModelId(),
                        projection.metadataExtractionEnabled(),
                        projection.reindexImpact());
        String integrity = sha256HexUtf8(canonicalJson(persist));
        persist.put(INTEGRITY_KEY, integrity);
        return persist;
    }

    public KnowledgeBuildProjection fromPersistedSnapshot(ResolvedConfigSnapshotEntity entity) {
        Map<String, Object> payload = entity.getPayloadJsonb();
        if (payload == null || !payload.containsKey(PAYLOAD_KEY)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "resolved_config_snapshot missing knowledgeBuildProjection for pin execute");
        }
        Object raw = payload.get(PAYLOAD_KEY);
        if (!(raw instanceof Map)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "knowledgeBuildProjection must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = new LinkedHashMap<>((Map<String, Object>) raw);
        Object integrityObj = nested.remove(INTEGRITY_KEY);
        if (integrityObj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "knowledgeBuildProjection missing integritySha256");
        }
        String expectedIntegrity = integrityObj.toString();
        String recomputed = sha256HexUtf8(canonicalJson(nested));
        if (!expectedIntegrity.equalsIgnoreCase(recomputed)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "knowledgeBuildProjection integrity check failed");
        }
        int version = readInt(nested, "projectionVersion", PROJECTION_VERSION);
        MaterializationStrategy strategy = MaterializationStrategy.valueOf(readString(nested, "materializationStrategy"));
        int chunkMax = readInt(nested, "chunkMaxChars", DEFAULT_CHUNK_MAX_CHARS);
        int overlap = readInt(nested, "chunkOverlap", DEFAULT_CHUNK_OVERLAP);
        String embedding = readString(nested, "embeddingModelId");
        boolean meta = readBool(nested, "metadataExtractionEnabled");
        ReindexImpact impact = parseReindexImpact(nested.get("reindexImpact"));
        String rowHash = entity.getConfigHash() != null ? entity.getConfigHash() : "";
        return new KnowledgeBuildProjection(
                version, strategy, chunkMax, overlap, embedding, meta, impact, entity.getId(), rowHash);
    }

    private Map<String, Object> buildPersistenceMap(
            MaterializationStrategy strategy,
            int chunkMax,
            int overlap,
            String embedding,
            boolean meta,
            ReindexImpact impact) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projectionVersion", PROJECTION_VERSION);
        m.put("materializationStrategy", strategy.name());
        m.put("chunkMaxChars", chunkMax);
        m.put("chunkOverlap", overlap);
        m.put("embeddingModelId", embedding);
        m.put("metadataExtractionEnabled", meta);
        m.put("reindexImpact", objectMapper.convertValue(impact, MAP_STRING_OBJECT));
        return m;
    }

    private String canonicalJson(Map<String, Object> mapWithoutIntegrity) {
        try {
            return objectMapper.writeValueAsString(mapWithoutIntegrity);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256HexUtf8(String utf8) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(utf8.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int readInt(Map<String, Object> m, String key, int defaultVal) {
        Object v = m.get(key);
        if (v == null) {
            return defaultVal;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(v.toString());
    }

    private static String readString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    private static boolean readBool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return v != null && Boolean.parseBoolean(v.toString());
    }

    @SuppressWarnings("unchecked")
    private ReindexImpact parseReindexImpact(Object raw) {
        if (raw == null) {
            return ReindexImpact.none();
        }
        Map<String, Object> m = objectMapper.convertValue(raw, MAP_STRING_OBJECT);
        String levelStr = m.get("level") != null ? m.get("level").toString() : "NO_REINDEX";
        ReindexImpactLevel level = ReindexImpactLevel.valueOf(levelStr);
        List<String> reasons = List.of();
        if (m.get("reasons") instanceof List<?> l) {
            reasons = l.stream().map(Object::toString).toList();
        }
        return new ReindexImpact(level, reasons);
    }
}
