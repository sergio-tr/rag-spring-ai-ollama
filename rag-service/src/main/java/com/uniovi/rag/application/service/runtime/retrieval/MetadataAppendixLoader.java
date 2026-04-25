package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.knowledge.DocumentArtifactType;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Snapshot-bound DB metadata appendix for {@link com.uniovi.rag.application.service.runtime.ChunkDenseMetadataWorkflow}.
 */
@Component
public class MetadataAppendixLoader {

    private final NamedParameterJdbcTemplate namedJdbc;

    public MetadataAppendixLoader(NamedParameterJdbcTemplate namedJdbc) {
        this.namedJdbc = namedJdbc;
    }

    public String loadAppendix(ExecutionContext ctx, QueryPlan plan, List<RetrievalCandidate> survivors) {
        java.util.Objects.requireNonNull(plan, "plan");
        List<UUID> snapshotIds = ctx.knowledgeSnapshotSelection().orderedSnapshotIds();
        if (ctx.projectId() == null || snapshotIds.isEmpty()) {
            return "";
        }
        LinkedHashSet<UUID> docIds = new LinkedHashSet<>();
        for (RetrievalCandidate c : survivors) {
            UUID id = parseProjectDocumentUuid(c.metadata());
            if (id != null) {
                docIds.add(id);
            }
        }
        if (docIds.isEmpty()) {
            return "";
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("projectId", ctx.projectId());
        p.addValue("docIds", new ArrayList<>(docIds));
        p.addValue("snapshotIds", snapshotIds);
        p.addValue("artifactType", DocumentArtifactType.METADATA.name());
        List<String> payloads =
                namedJdbc.query(
                        """
                        SELECT da.payload_jsonb::text
                        FROM document_artifact da
                        JOIN project_documents kd ON kd.id = da.document_id
                        WHERE da.artifact_type = CAST(:artifactType AS VARCHAR)
                          AND kd.project_id = :projectId
                          AND kd.id IN (:docIds)
                          AND kd.current_index_snapshot_id IN (:snapshotIds)
                        """,
                        p,
                        (rs, rowNum) -> rs.getString(1));
        if (payloads.isEmpty()) {
            return "";
        }
        return payloads.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> "[metadata] " + s)
                .collect(Collectors.joining("\n"));
    }

    private static UUID parseProjectDocumentUuid(Map<String, Object> meta) {
        if (meta == null) {
            return null;
        }
        Object id = meta.get("document_id");
        if (id == null) {
            id = meta.get("documentId");
        }
        if (id == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
