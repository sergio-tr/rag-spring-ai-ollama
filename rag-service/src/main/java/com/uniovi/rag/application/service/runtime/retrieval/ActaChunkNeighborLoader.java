package com.uniovi.rag.application.service.runtime.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Loads sibling acta chunks from {@code vector_store} for section/neighbor context expansion.
 */
@Component
public class ActaChunkNeighborLoader {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final String SELECT_COLUMNS =
            """
            SELECT content, metadata::text AS metadata_json, chunk_index
            FROM vector_store
            WHERE project_id = ?
              AND metadata->>'indexSnapshotId' = ?
              AND metadata->>'projectDocumentId' = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ActaChunkNeighborLoader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<RetrievalCandidate> loadSectionSiblings(
            UUID projectId,
            UUID snapshotId,
            String projectDocumentId,
            String sectionType,
            int neighborRadius) {
        if (projectId == null
                || snapshotId == null
                || projectDocumentId == null
                || projectDocumentId.isBlank()
                || sectionType == null
                || sectionType.isBlank()) {
            return List.of();
        }
        String sql =
                SELECT_COLUMNS
                        + """
                          AND metadata->>'sectionType' = ?
                        ORDER BY chunk_index NULLS LAST, id
                        """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) ->
                        toCandidate(
                                snapshotId,
                                rs.getString("content"),
                                rs.getString("metadata_json"),
                                resolveChunkIndex(rs.getObject("chunk_index"), rs.getString("metadata_json"))),
                projectId,
                snapshotId.toString(),
                projectDocumentId,
                sectionType);
    }

    public List<RetrievalCandidate> loadNeighborChunks(
            UUID projectId,
            UUID snapshotId,
            String projectDocumentId,
            int centerChunkIndex,
            int neighborRadius) {
        if (projectId == null
                || snapshotId == null
                || projectDocumentId == null
                || projectDocumentId.isBlank()) {
            return List.of();
        }
        int radius = Math.max(0, neighborRadius);
        int minIdx = Math.max(0, centerChunkIndex - radius);
        int maxIdx = centerChunkIndex + radius;
        String sql =
                SELECT_COLUMNS
                        + """
                          AND chunk_index BETWEEN ? AND ?
                        ORDER BY chunk_index NULLS LAST, id
                        """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) ->
                        toCandidate(
                                snapshotId,
                                rs.getString("content"),
                                rs.getString("metadata_json"),
                                resolveChunkIndex(rs.getObject("chunk_index"), rs.getString("metadata_json"))),
                projectId,
                snapshotId.toString(),
                projectDocumentId,
                minIdx,
                maxIdx);
    }

    private int resolveChunkIndex(Object chunkIndexColumn, String metadataJson) {
        if (chunkIndexColumn instanceof Number number) {
            return number.intValue();
        }
        Map<String, Object> meta = parseMetadata(metadataJson);
        Integer fromMeta = chunkIndex(meta);
        return fromMeta != null ? fromMeta : 0;
    }

    private static Integer chunkIndex(Map<String, Object> meta) {
        if (meta == null) {
            return null;
        }
        for (String key : List.of("chunkIndex", "chunk_index")) {
            Object raw = meta.get(key);
            if (raw instanceof Number number) {
                return number.intValue();
            }
            if (raw != null) {
                try {
                    return Integer.parseInt(raw.toString().trim());
                } catch (NumberFormatException ignored) {
                    // continue
                }
            }
        }
        return null;
    }

    private RetrievalCandidate toCandidate(
            UUID snapshotId, String content, String metadataJson, int chunkIndex) {
        Map<String, Object> meta = parseMetadata(metadataJson);
        meta.putIfAbsent("chunkIndex", chunkIndex);
        String candidateId = RetrievalCandidateIds.fromSparseRow(snapshotId, meta, chunkIndex);
        return new RetrievalCandidate(
                candidateId, content != null ? content : "", meta, 0, 0, 0, 0, snapshotId, 0);
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(metadataJson, MAP_TYPE));
        } catch (Exception ignored) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("rawMetadata", metadataJson);
            return fallback;
        }
    }
}
