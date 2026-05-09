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

    public static final String REASON_CODE = "CORPUS_REQUIRED";

    public static final String REASON_MESSAGE =
            "This preset requires project documents/corpus, but none was available.";

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
        m.put("readySharedDocsWithStorageUriCount", r.readySharedDocsWithUriCount());
        m.put("vectorChunkRowCount", r.vectorChunkRowCount());
        if (!r.satisfied()) {
            m.put("skippedReasonCode", REASON_CODE);
        }
        return Map.copyOf(m);
    }

    public Result evaluate(UUID projectId, List<UUID> snapshotIds) {
        if (projectId == null) {
            return new Result(false, 0, 0, REASON_CODE, REASON_MESSAGE);
        }
        int docsReady =
                (int)
                        knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(
                                        projectId, CorpusScope.PROJECT_SHARED)
                                .stream()
                                .filter(CorpusAvailabilityGate::readyWithUri)
                                .count();
        if (docsReady <= 0) {
            return new Result(false, docsReady, 0, REASON_CODE, REASON_MESSAGE);
        }
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            return new Result(false, docsReady, 0, REASON_CODE, REASON_MESSAGE);
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
            return new Result(false, docsReady, rows, REASON_CODE, REASON_MESSAGE);
        }
        return new Result(true, docsReady, rows, null, null);
    }

    private static boolean readyWithUri(KnowledgeDocumentEntity d) {
        return d != null
                && d.getStatus() == ProjectDocumentStatus.READY
                && d.getStorageUri() != null
                && !d.getStorageUri().isBlank();
    }

    public record Result(
            boolean satisfied,
            int readySharedDocsWithUriCount,
            long vectorChunkRowCount,
            String reasonCode,
            String reasonMessage) {}
}
