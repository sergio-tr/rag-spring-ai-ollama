package com.uniovi.rag.service.evaluation.preset;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Lab preflight gate ensuring presets that assemble corpus from {@code vector_store} have READY shared documents,
 * an explicit snapshot id, and non-empty indexed chunk rows before invoking retrieval-grounded workflows.
 */
@Service
public class CorpusAvailabilityGate {

    public static final String REASON_CODE = "CORPUS_EVIDENCE_UNAVAILABLE";

    public static final String NO_CORPUS_SELECTED = "NO_CORPUS_SELECTED";

    public static final String NO_DOCUMENTS = "NO_DOCUMENTS";

    public static final String NO_READY_DOCUMENTS = "NO_READY_DOCUMENTS";

    public static final String REINDEX_REQUIRED = "REINDEX_REQUIRED";

    public static final String SNAPSHOT_VECTOR_ROWS_MISSING = "SNAPSHOT_INCOMPATIBLE";

    public static final String REASON_MESSAGE =
            "This preset requires corpus evidence, but usable evidence could not be assembled.";

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public CorpusAvailabilityGate(
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    /** Structured diagnostics merged into Lab metrics payloads (never blocks callers beyond gate semantics). */
    public Map<String, Object> probe(UUID projectId, List<UUID> snapshotIds) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        Result r = evaluate(projectId, snapshotIds);
        m.put("corpusRequired", true);
        m.put("corpusAvailable", r.satisfied());
        m.put("evaluationCorpusProjectId", projectId != null ? projectId.toString() : null);
        m.put("evaluationCorpusDocumentIds", r.readyDocumentIds().stream().map(UUID::toString).toList());
        m.put("projectDocumentCount", r.projectDocumentCount());
        m.put("readySharedDocsWithStorageUriCount", r.readySharedDocsWithUriCount());
        m.put("vectorChunkRowCount", r.vectorChunkRowCount());
        if (!r.satisfied()) {
            m.put("skippedReasonCode", r.reasonCode());
            m.put("skippedReason", r.reasonMessage());
        }
        return Map.copyOf(m);
    }

    public Result evaluate(UUID projectId, List<UUID> snapshotIds) {
        if (projectId == null) {
            return failed(0, List.of(), 0, NO_CORPUS_SELECTED, "No evaluation corpus/project was selected.");
        }
        List<KnowledgeDocumentEntity> docs = knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(
                projectId, CorpusScope.PROJECT_SHARED);
        if (docs == null || docs.isEmpty()) {
            return failed(0, List.of(), 0, NO_DOCUMENTS, "The selected evaluation corpus has no documents.");
        }
        List<UUID> readyDocIds =
                docs.stream()
                        .filter(CorpusAvailabilityGate::readyWithUri)
                        .map(KnowledgeDocumentEntity::getId)
                        .toList();
        int docsReady = readyDocIds.size();
        if (docsReady <= 0) {
            return failed(
                    docs.size(),
                    readyDocIds,
                    0,
                    NO_READY_DOCUMENTS,
                    "The selected evaluation corpus has documents, but none are READY with stored content.");
        }
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            return failed(
                    docs.size(),
                    readyDocIds,
                    0,
                    REINDEX_REQUIRED,
                    "Documents are READY, but no snapshot was selected for corpus evidence.");
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("projectId", projectId);
        p.addValue("snapshotIds", snapshotIds.stream().map(UUID::toString).toList());
        Long cnt =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM vector_store
                        WHERE project_id = :projectId
                          AND (metadata->>'indexSnapshotId')::uuid IN (:snapshotIds)
                        """,
                        p,
                        Long.class);
        long rows = cnt != null ? cnt : 0L;
        if (rows <= 0) {
            return failed(
                    docs.size(),
                    readyDocIds,
                    rows,
                    SNAPSHOT_VECTOR_ROWS_MISSING,
                    "A snapshot was selected, but it has no vector rows for the evaluation corpus.");
        }
        return new Result(true, docs.size(), readyDocIds, docsReady, rows, null, null);
    }

    private static Result failed(
            int projectDocumentCount,
            List<UUID> readyDocumentIds,
            long vectorRows,
            String reasonCode,
            String reasonMessage) {
        return new Result(
                false,
                projectDocumentCount,
                readyDocumentIds != null ? List.copyOf(readyDocumentIds) : List.of(),
                readyDocumentIds != null ? readyDocumentIds.size() : 0,
                vectorRows,
                reasonCode,
                reasonMessage);
    }

    private static boolean readyWithUri(KnowledgeDocumentEntity d) {
        return d != null
                && d.getStatus() == ProjectDocumentStatus.READY
                && d.getStorageUri() != null
                && !d.getStorageUri().isBlank();
    }

    public record Result(
            boolean satisfied,
            int projectDocumentCount,
            List<UUID> readyDocumentIds,
            int readySharedDocsWithUriCount,
            long vectorChunkRowCount,
            String reasonCode,
            String reasonMessage) {}
}
