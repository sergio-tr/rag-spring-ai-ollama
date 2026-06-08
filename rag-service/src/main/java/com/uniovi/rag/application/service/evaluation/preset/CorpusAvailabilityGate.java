package com.uniovi.rag.application.service.evaluation.preset;

import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
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

    /** Snapshot selected but has no {@code vector_store} rows for the evaluation corpus. */
    public static final String SNAPSHOT_VECTOR_ROWS_MISSING = "SNAPSHOT_INCOMPATIBLE";

    public static final String NO_COMPATIBLE_SNAPSHOT = "NO_COMPATIBLE_SNAPSHOT";

    public static final String REASON_MESSAGE =
            "This preset requires corpus evidence, but usable evidence could not be assembled.";

    private final EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public CorpusAvailabilityGate(
            EvaluationCorpusApplicationService evaluationCorpusApplicationService,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    /**
     * Preset-aware gate: P0/P1 and other non-retrieval presets require READY documents only; retrieval presets
     * additionally require non-empty {@code vector_store} rows for the bound snapshot.
     */
    public Result evaluateForPreset(
            UUID userId, UUID corpusId, List<UUID> snapshotIds, RagExperimentalPresetCode preset) {
        if (preset != null && !ExperimentalPresetCanonicalCatalog.needsVectorIndex(preset)) {
            return evaluateDocumentsOnly(userId, corpusId);
        }
        return evaluate(userId, corpusId, snapshotIds);
    }

    /** Structured diagnostics merged into Lab metrics payloads (never blocks callers beyond gate semantics). */
    public Map<String, Object> probe(UUID userId, UUID corpusId, List<UUID> snapshotIds) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        Result r = evaluate(userId, corpusId, snapshotIds);
        m.put("corpusRequired", true);
        m.put("corpusAvailable", r.satisfied());
        m.put("evaluationCorpusId", corpusId != null ? corpusId.toString() : null);
        m.put("evaluationCorpusDocumentIds", r.readyDocumentIds().stream().map(UUID::toString).toList());
        m.put("corpusDocumentCount", r.corpusDocumentCount());
        m.put("readySharedDocsWithStorageUriCount", r.readySharedDocsWithUriCount());
        m.put("vectorChunkRowCount", r.vectorChunkRowCount());
        List<String> selectedSnapshotIds =
                snapshotIds != null
                        ? snapshotIds.stream().filter(id -> id != null).map(UUID::toString).toList()
                        : List.of();
        m.put("selectedSnapshotIds", selectedSnapshotIds);
        if (selectedSnapshotIds.isEmpty()) {
            m.put(
                    "snapshotSelectionReason",
                    "No snapshot id was bound for corpus evidence (prepare index or enable auto-reindex).");
        }
        if (!r.satisfied()) {
            m.put("skippedReasonCode", r.reasonCode());
            m.put("skippedReason", r.reasonMessage());
        }
        return Map.copyOf(m);
    }

    public Map<String, Object> probeForPreset(
            UUID userId, UUID corpusId, List<UUID> snapshotIds, RagExperimentalPresetCode preset) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        Result r = evaluateForPreset(userId, corpusId, snapshotIds, preset);
        m.put("corpusRequired", preset == null || ExperimentalPresetCanonicalCatalog.corpusRequired(preset));
        m.put("corpusAvailable", r.satisfied());
        m.put("evaluationCorpusId", corpusId != null ? corpusId.toString() : null);
        m.put("evaluationCorpusDocumentIds", r.readyDocumentIds().stream().map(UUID::toString).toList());
        m.put("corpusDocumentCount", r.corpusDocumentCount());
        m.put("readySharedDocsWithStorageUriCount", r.readySharedDocsWithUriCount());
        m.put("vectorChunkRowCount", r.vectorChunkRowCount());
        List<String> selectedSnapshotIds =
                snapshotIds != null
                        ? snapshotIds.stream().filter(id -> id != null).map(UUID::toString).toList()
                        : List.of();
        m.put("selectedSnapshotIds", selectedSnapshotIds);
        if (!r.satisfied()) {
            m.put("skippedReasonCode", r.reasonCode());
            m.put("skippedReason", r.reasonMessage());
        }
        return Map.copyOf(m);
    }

    /** READY evaluation-corpus documents without vector index requirements (P0/P1 direct paths). */
    public Result evaluateDocumentsOnly(UUID userId, UUID corpusId) {
        if (corpusId == null) {
            return failed(0, List.of(), 0, NO_CORPUS_SELECTED, "No evaluation corpus was selected.");
        }
        EvaluationCorpusApplicationService.EvaluationCorpusContext context;
        try {
            context = evaluationCorpusApplicationService.requireContext(userId, corpusId);
        } catch (Exception ex) {
            return failed(0, List.of(), 0, NO_CORPUS_SELECTED, "No evaluation corpus was selected.");
        }
        List<KnowledgeDocumentEntity> docs = context.documents();
        if (docs == null || docs.isEmpty()) {
            return failed(0, List.of(), 0, NO_DOCUMENTS, "The selected evaluation corpus has no documents.");
        }
        List<UUID> readyDocIds = context.readyDocumentIds();
        int docsReady = readyDocIds.size();
        if (docsReady <= 0) {
            return failed(
                    docs.size(),
                    readyDocIds,
                    0,
                    NO_READY_DOCUMENTS,
                    "The selected evaluation corpus has documents, but none are READY with stored content.");
        }
        return new Result(true, docs.size(), readyDocIds, docsReady, 0L, null, null);
    }

    /** Counts {@code vector_store} rows bound to the evaluation corpus project and snapshot ids. */
    public long countVectorRows(UUID indexProjectId, List<UUID> snapshotIds) {
        if (indexProjectId == null || snapshotIds == null || snapshotIds.isEmpty()) {
            return 0L;
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("projectId", indexProjectId);
        p.addValue("snapshotIds", snapshotIds);
        Long cnt =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM vector_store
                        WHERE project_id = :projectId
                          AND (metadata->>'indexSnapshotId')::uuid IN (:snapshotIds)
                        """,
                        p,
                        Long.class);
        return cnt != null ? cnt : 0L;
    }

    public boolean snapshotHasVectorRows(UUID userId, UUID corpusId, UUID snapshotId) {
        if (snapshotId == null) {
            return false;
        }
        EvaluationCorpusApplicationService.EvaluationCorpusContext context;
        try {
            context = evaluationCorpusApplicationService.requireContext(userId, corpusId);
        } catch (Exception ex) {
            return false;
        }
        UUID indexProjectId = context.indexProjectId();
        if (indexProjectId == null) {
            return false;
        }
        return countVectorRows(indexProjectId, List.of(snapshotId)) > 0L;
    }

    public Result evaluate(UUID userId, UUID corpusId, List<UUID> snapshotIds) {
        if (corpusId == null) {
            return failed(0, List.of(), 0, NO_CORPUS_SELECTED, "No evaluation corpus was selected.");
        }
        EvaluationCorpusApplicationService.EvaluationCorpusContext context;
        try {
            context = evaluationCorpusApplicationService.requireContext(userId, corpusId);
        } catch (Exception ex) {
            return failed(0, List.of(), 0, NO_CORPUS_SELECTED, "No evaluation corpus was selected.");
        }
        List<KnowledgeDocumentEntity> docs = context.documents();
        if (docs == null || docs.isEmpty()) {
            return failed(0, List.of(), 0, NO_DOCUMENTS, "The selected evaluation corpus has no documents.");
        }
        List<UUID> readyDocIds = context.readyDocumentIds();
        int docsReady = readyDocIds.size();
        if (docsReady <= 0) {
            return failed(
                    docs.size(),
                    readyDocIds,
                    0,
                    NO_READY_DOCUMENTS,
                    "The selected evaluation corpus has documents, but none are READY with stored content.");
        }
        UUID indexProjectId = context.indexProjectId();
        if (indexProjectId == null) {
            return failed(
                    docs.size(),
                    readyDocIds,
                    0,
                    NO_CORPUS_SELECTED,
                    "The selected evaluation corpus has no index scope.");
        }
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            return failed(
                    docs.size(),
                    readyDocIds,
                    0,
                    REINDEX_REQUIRED,
                    "Documents are READY, but no snapshot was selected for corpus evidence.");
        }
        long rows = countVectorRows(indexProjectId, snapshotIds);
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
            int corpusDocumentCount,
            List<UUID> readyDocumentIds,
            long vectorRows,
            String reasonCode,
            String reasonMessage) {
        return new Result(
                false,
                corpusDocumentCount,
                readyDocumentIds != null ? List.copyOf(readyDocumentIds) : List.of(),
                readyDocumentIds != null ? readyDocumentIds.size() : 0,
                vectorRows,
                reasonCode,
                reasonMessage);
    }

    public record Result(
            boolean satisfied,
            int corpusDocumentCount,
            List<UUID> readyDocumentIds,
            int readySharedDocsWithUriCount,
            long vectorChunkRowCount,
            String reasonCode,
            String reasonMessage) {}
}
