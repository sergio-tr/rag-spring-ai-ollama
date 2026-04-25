package com.uniovi.rag.application.service.runtime.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Snapshot-bound PostgreSQL full-text search over {@code vector_store.content} (generated {@code content_tsv}).
 */
@Service
public class SparseRetrievalStrategy {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SparseRetrievalStrategy(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RetrievalCandidate> retrieve(RetrievalRequest req) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("query", req.queryText());
        p.addValue("projectId", req.projectId());
        p.addValue("snapshotIds", req.snapshotIds());
        p.addValue("limit", req.topKSparse());

        StringBuilder sql = new StringBuilder(
                """
                SELECT id, content, metadata::text AS metadata_json, chunk_index,
                  ts_rank_cd(content_tsv, websearch_to_tsquery('simple', :query)) AS rank
                FROM vector_store
                WHERE (project_id IS NOT DISTINCT FROM CAST(:projectId AS uuid))
                  AND (metadata->>'indexSnapshotId') IS NOT NULL
                  AND (metadata->>'indexSnapshotId')::uuid IN (:snapshotIds)
                  AND content_tsv @@ websearch_to_tsquery('simple', :query)
                """);

        if (!req.documentAllowlistIsAll()) {
            List<UUID> docUuids = new ArrayList<>();
            for (String s : req.documentAllowlist()) {
                if (s == null || s.isBlank() || "all".equalsIgnoreCase(s.trim())) {
                    continue;
                }
                try {
                    docUuids.add(UUID.fromString(s.trim()));
                } catch (IllegalArgumentException ignored) {
                    // skip non-UUID entries
                }
            }
            if (docUuids.isEmpty()) {
                return List.of();
            }
            p.addValue("docIds", docUuids);
            sql.append(" AND (metadata->>'document_id')::uuid IN (:docIds) ");
        }

        sql.append(" ORDER BY rank DESC NULLS LAST LIMIT :limit ");

        try {
            List<RetrievalCandidate> rows =
                    jdbc.query(
                            sql.toString(),
                            p,
                            (rs, rowNum) -> {
                                String metaJson = rs.getString("metadata_json");
                                Map<String, Object> meta = parseMetadata(metaJson);
                                Object sidObj = meta.get("indexSnapshotId");
                                if (sidObj == null) {
                                    return null;
                                }
                                UUID snapshotId = UUID.fromString(String.valueOf(sidObj));
                                Integer chunkIndex = (Integer) rs.getObject("chunk_index");
                                if (rs.wasNull()) {
                                    chunkIndex = null;
                                }
                                String cid = RetrievalCandidateIds.fromSparseRow(snapshotId, meta, chunkIndex);
                                double rank = rs.getDouble("rank");
                                int sparseRank = rowNum + 1;
                                double rrfProxy = 1.0 / (RetrievalPolicy.RRF_K + sparseRank);
                                return new RetrievalCandidate(
                                        cid,
                                        rs.getString("content") != null ? rs.getString("content") : "",
                                        meta,
                                        Double.NaN,
                                        rank,
                                        0,
                                        sparseRank,
                                        snapshotId,
                                        rrfProxy);
                            });
            return rows.stream().filter(Objects::nonNull).collect(Collectors.toList());
        } catch (Exception e) {
            throw RagServiceException.hybridSparseRetrievalFailed(e);
        }
    }

    private Map<String, Object> parseMetadata(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metaJson, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw RagServiceException.hybridSparseRetrievalFailed(e);
        }
    }
}
