package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Snapshot-bound full-corpus text assembly for {@code FullCorpusWorkflow}. Sole gateway for this path to
 * {@code vector_store} reads.
 */
@Service
public class SnapshotCorpusAssembler {

    public record CorpusDocumentRef(String documentId, String filename) {}

    private static final String SEPARATOR = "\n\n--- chunk ---\n\n";

    private final NamedParameterJdbcTemplate namedJdbc;

    public SnapshotCorpusAssembler(NamedParameterJdbcTemplate namedJdbc) {
        this.namedJdbc = namedJdbc;
    }

    public String assembleFullCorpusText(ExecutionContext ctx) {
        KnowledgeSnapshotSelection snap = ctx.knowledgeSnapshotSelection();
        List<UUID> snapshotIds = snap.orderedSnapshotIds();
        if (snapshotIds.isEmpty()) {
            throw new IllegalStateException("SnapshotCorpusAssembler requires non-empty orderedSnapshotIds");
        }
        RagExecutionContext holder = RagExecutionContextHolder.get();
        if (holder == null || holder.resolvedConfig() == null) {
            throw new IllegalStateException("RagExecutionContextHolder must be set for corpus assembly");
        }
        if (!holder.restrictsByProject()) {
            return "";
        }
        UUID projectId;
        try {
            projectId = UUID.fromString(holder.projectId().trim());
        } catch (Exception e) {
            return "";
        }
        int maxChars = Math.max(1024, holder.resolvedConfig().naiveFullCorpusMaxChars());
        List<String> contents = fetchContents(projectId, holder, snapshotIds);
        if (contents.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(maxChars, 65536));
        for (String c : contents) {
            if (c == null || c.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(SEPARATOR);
            }
            sb.append(c.trim());
            if (sb.length() >= maxChars) {
                sb.setLength(maxChars);
                break;
            }
        }
        String out = sb.toString().trim();
        if (out.length() > maxChars) {
            out = out.substring(0, maxChars);
        }
        return out;
    }

    /** Distinct corpus documents for source attribution on full-corpus answers. */
    public List<CorpusDocumentRef> listCorpusDocuments(ExecutionContext ctx) {
        KnowledgeSnapshotSelection snap = ctx.knowledgeSnapshotSelection();
        List<UUID> snapshotIds = snap.orderedSnapshotIds();
        if (snapshotIds.isEmpty()) {
            return List.of();
        }
        RagExecutionContext holder = RagExecutionContextHolder.get();
        if (holder == null || holder.resolvedConfig() == null || !holder.restrictsByProject()) {
            return List.of();
        }
        UUID projectId;
        try {
            projectId = UUID.fromString(holder.projectId().trim());
        } catch (Exception e) {
            return List.of();
        }
        return fetchDistinctDocuments(projectId, holder, snapshotIds);
    }

    private List<String> fetchContents(UUID projectId, RagExecutionContext ctx, List<UUID> snapshotIds) {
        assertSnapshotFilter(snapshotIds);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("projectId", projectId);
        p.addValue("snapshotIds", snapshotIds.stream().map(UUID::toString).toList());
        if (ctx.documentFilterIsAll()) {
            return namedJdbc.query(
                    """
                    SELECT content FROM vector_store
                    WHERE project_id = :projectId
                      AND metadata->>'indexSnapshotId' IN (:snapshotIds)
                    ORDER BY COALESCE(chunk_index, 0), id
                    """,
                    p,
                    (rs, rowNum) -> rs.getString(1));
        }
        Set<String> allowed = new HashSet<>();
        for (String id : ctx.documentFilter()) {
            if (id != null && !id.isBlank() && !RagExecutionContext.ALL_DOCUMENTS.equalsIgnoreCase(id.trim())) {
                allowed.add(id.trim());
            }
        }
        if (allowed.isEmpty()) {
            return List.of();
        }
        p.addValue("docIds", new ArrayList<>(allowed));
        return namedJdbc.query(
                """
                SELECT content FROM vector_store
                WHERE project_id = :projectId
                  AND metadata->>'indexSnapshotId' IN (:snapshotIds)
                  AND metadata->>'document_id' IN (:docIds)
                ORDER BY COALESCE(chunk_index, 0), id
                """,
                p,
                (rs, rowNum) -> rs.getString(1));
    }

    private List<CorpusDocumentRef> fetchDistinctDocuments(
            UUID projectId, RagExecutionContext ctx, List<UUID> snapshotIds) {
        assertSnapshotFilter(snapshotIds);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("projectId", projectId);
        p.addValue("snapshotIds", snapshotIds.stream().map(UUID::toString).toList());
        if (ctx.documentFilterIsAll()) {
            return namedJdbc.query(
                    """
                    SELECT DISTINCT
                      metadata->>'document_id' AS document_id,
                      metadata->>'filename' AS filename
                    FROM vector_store
                    WHERE project_id = :projectId
                      AND metadata->>'indexSnapshotId' IN (:snapshotIds)
                    ORDER BY filename NULLS LAST, document_id
                    """,
                    p,
                    (rs, rowNum) ->
                            new CorpusDocumentRef(
                                    rs.getString("document_id"), rs.getString("filename")));
        }
        Set<String> allowed = new HashSet<>();
        for (String id : ctx.documentFilter()) {
            if (id != null && !id.isBlank() && !RagExecutionContext.ALL_DOCUMENTS.equalsIgnoreCase(id.trim())) {
                allowed.add(id.trim());
            }
        }
        if (allowed.isEmpty()) {
            return List.of();
        }
        p.addValue("docIds", new ArrayList<>(allowed));
        return namedJdbc.query(
                """
                SELECT DISTINCT
                  metadata->>'document_id' AS document_id,
                  metadata->>'filename' AS filename
                FROM vector_store
                WHERE project_id = :projectId
                  AND metadata->>'indexSnapshotId' IN (:snapshotIds)
                  AND metadata->>'document_id' IN (:docIds)
                ORDER BY filename NULLS LAST, document_id
                """,
                p,
                (rs, rowNum) ->
                        new CorpusDocumentRef(rs.getString("document_id"), rs.getString("filename")));
    }

    private static void assertSnapshotFilter(List<UUID> snapshotIds) {
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            throw new IllegalStateException("orderedSnapshotIds must be non-empty for vector_store reads");
        }
    }

}
