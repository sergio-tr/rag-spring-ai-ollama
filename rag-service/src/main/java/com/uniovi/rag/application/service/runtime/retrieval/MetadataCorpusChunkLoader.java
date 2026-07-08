package com.uniovi.rag.application.service.runtime.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.RagSnapshotContextHolder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Single-query loader for snapshot-bound acta chunks. Avoids repeated embedding similarity searches on
 * small corpora during deterministic metadata-tool paths.
 */
@Component
public class MetadataCorpusChunkLoader {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MetadataCorpusChunkLoader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Document> loadScopedCorpusChunks() {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx == null || !ctx.restrictsByProject()) {
            return List.of();
        }
        UUID projectId;
        try {
            projectId = UUID.fromString(ctx.projectId().trim());
        } catch (Exception e) {
            return List.of();
        }
        Set<String> snapshotIds = RagSnapshotContextHolder.activeSnapshotIds();
        if (snapshotIds.isEmpty()) {
            return List.of();
        }
        List<String> snapshotIdList = snapshotIds.stream().sorted().toList();
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT id::text AS row_id, content, metadata::text AS metadata_json, chunk_index
                        FROM vector_store
                        WHERE project_id = ?
                          AND metadata->>'indexSnapshotId' IN (
                        """);
        for (int i = 0; i < snapshotIdList.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
            args.add(snapshotIdList.get(i));
        }
        sql.append(") ");
        if (!ctx.documentFilterIsAll()) {
            List<String> allowed = new ArrayList<>();
            for (String id : ctx.documentFilter()) {
                if (id != null && !id.isBlank()) {
                    allowed.add(id.trim());
                }
            }
            if (allowed.isEmpty()) {
                return List.of();
            }
            sql.append(" AND metadata->>'document_id' IN (");
            for (int i = 0; i < allowed.size(); i++) {
                if (i > 0) {
                    sql.append(',');
                }
                sql.append('?');
                args.add(allowed.get(i));
            }
            sql.append(") ");
        }
        sql.append(" ORDER BY COALESCE(chunk_index, 0), id");

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> {
                    String content = rs.getString("content");
                    Map<String, Object> meta = parseMetadata(rs.getString("metadata_json"));
                    Object chunkIdx = rs.getObject("chunk_index");
                    if (chunkIdx instanceof Number number) {
                        meta.putIfAbsent("chunkIndex", number.intValue());
                        meta.putIfAbsent("chunk_index", number.intValue());
                    }
                    String rowId = rs.getString("row_id");
                    if (rowId != null && !rowId.isBlank()) {
                        meta.putIfAbsent("vectorStoreRowId", rowId);
                    }
                    return new Document(content != null ? content : "", meta);
                },
                args.toArray());
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
