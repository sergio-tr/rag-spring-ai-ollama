package com.uniovi.rag.application.service.runtime.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.domain.runtime.retrieval.SparseQueryPreparation;
import com.uniovi.rag.domain.runtime.retrieval.SparseRetrievalFallbackStage;
import com.uniovi.rag.domain.runtime.retrieval.SparseRetrievalTelemetry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Snapshot-bound PostgreSQL full-text search over {@code vector_store.content} (generated {@code content_tsv}).
 */
@Service
public class SparseRetrievalStrategy {

    private static final Logger log = LoggerFactory.getLogger(SparseRetrievalStrategy.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final SparseQueryPreparer sparseQueryPreparer;
    private final SparseDomainSynonyms synonyms;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Boolean ilikeSupported;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate nestedTransactionTemplate;
    private final boolean savepointsSupported;

    @Autowired
    public SparseRetrievalStrategy(
            NamedParameterJdbcTemplate jdbc,
            SparseQueryPreparer sparseQueryPreparer,
            SparseDomainSynonyms synonyms,
            PlatformTransactionManager transactionManager) {
        this(
                jdbc,
                sparseQueryPreparer,
                synonyms,
                probeIlikeSupport(jdbc),
                transactionManager,
                probeSavepointSupport(transactionManager));
    }

    /** Explicit ILIKE probe override for unit/integration tests (no transaction savepoints). */
    public SparseRetrievalStrategy(
            NamedParameterJdbcTemplate jdbc,
            SparseQueryPreparer sparseQueryPreparer,
            SparseDomainSynonyms synonyms,
            boolean ilikeSupported) {
        this(jdbc, sparseQueryPreparer, synonyms, ilikeSupported, null, false);
    }

    /** Integration/tests without {@link PlatformTransactionManager} (savepoint isolation disabled). */
    public SparseRetrievalStrategy(
            NamedParameterJdbcTemplate jdbc,
            SparseQueryPreparer sparseQueryPreparer,
            SparseDomainSynonyms synonyms) {
        this(jdbc, sparseQueryPreparer, synonyms, probeIlikeSupport(jdbc), null, false);
    }

    SparseRetrievalStrategy(
            NamedParameterJdbcTemplate jdbc,
            SparseQueryPreparer sparseQueryPreparer,
            SparseDomainSynonyms synonyms,
            boolean ilikeSupported,
            PlatformTransactionManager transactionManager) {
        this(
                jdbc,
                sparseQueryPreparer,
                synonyms,
                ilikeSupported,
                transactionManager,
                probeSavepointSupport(transactionManager));
    }

    SparseRetrievalStrategy(
            NamedParameterJdbcTemplate jdbc,
            SparseQueryPreparer sparseQueryPreparer,
            SparseDomainSynonyms synonyms,
            boolean ilikeSupported,
            PlatformTransactionManager transactionManager,
            boolean savepointsSupported) {
        this.jdbc = jdbc;
        this.sparseQueryPreparer = sparseQueryPreparer;
        this.synonyms = synonyms;
        this.ilikeSupported = ilikeSupported;
        this.savepointsSupported = transactionManager != null && savepointsSupported;
        this.transactionTemplate =
                transactionManager != null ? new TransactionTemplate(transactionManager) : null;
        if (transactionManager != null) {
            this.nestedTransactionTemplate = new TransactionTemplate(transactionManager);
            nestedTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        } else {
            this.nestedTransactionTemplate = null;
        }
        if (transactionManager != null && !this.savepointsSupported) {
            log.warn(
                    "JPA dialect does not support savepoints - sparse retrieval SQL runs in REQUIRES_NEW"
                            + " transactions with controlled fallback (dense/metadata retrieval continues)");
        }
    }

    public List<RetrievalCandidate> retrieve(RetrievalRequest req) {
        return retrieve(req, null).candidates();
    }

    public SparseRetrievalOutcome retrieve(RetrievalRequest req, QueryPlan plan) {
        SparseQueryPreparation preparation = sparseQueryPreparer.prepare(req.queryText(), plan);
        if (preparation.normalizedQuery().isBlank()
                && preparation.keywordTerms().isEmpty()
                && preparation.exactPhrases().isEmpty()
                && preparation.entityTerms().isEmpty()) {
            return noHit(preparation, "", SparseRetrievalFallbackStage.NO_HIT, "blank_query");
        }

        LinkedHashSet<String> seenIds = new LinkedHashSet<>();
        List<RetrievalCandidate> accumulated = new ArrayList<>();
        int limit = Math.max(1, req.topKSparse());
        StageAttempt lastAttempt = null;

        for (StageAttempt stage : buildStageAttempts(preparation)) {
            lastAttempt = stage;
            List<RetrievalCandidate> hits;
            try {
                hits = executeStage(req, stage);
            } catch (Exception ex) {
                log.warn(
                        "Sparse retrieval stage {} failed (query='{}'): {}",
                        stage.stage(),
                        truncateForLog(stage.queryText(), 120),
                        ex.getMessage());
                continue;
            }
            for (RetrievalCandidate c : hits) {
                if (c == null || c.candidateId() == null || seenIds.contains(c.candidateId())) {
                    continue;
                }
                seenIds.add(c.candidateId());
                accumulated.add(c);
                if (accumulated.size() >= limit) {
                    break;
                }
            }
            if (!accumulated.isEmpty()) {
                SparseRetrievalTelemetry telemetry =
                        new SparseRetrievalTelemetry(
                                preparation.originalQuery(),
                                stage.queryText(),
                                stage.stage(),
                                true,
                                "");
                return new SparseRetrievalOutcome(
                        List.copyOf(accumulated), preparation, stage.stage(), stage.queryText(), telemetry);
            }
        }

        String rewritten =
                lastAttempt != null ? lastAttempt.queryText() : lastAttemptQuery(preparation);
        SparseRetrievalFallbackStage lastStage =
                lastAttempt != null ? lastAttempt.stage() : SparseRetrievalFallbackStage.NO_HIT;
        return noHit(preparation, rewritten, lastStage, "no_lexical_match");
    }

    private SparseRetrievalOutcome noHit(
            SparseQueryPreparation preparation,
            String rewritten,
            SparseRetrievalFallbackStage lastStage,
            String reason) {
        SparseRetrievalTelemetry telemetry =
                new SparseRetrievalTelemetry(
                        preparation.originalQuery(),
                        rewritten,
                        lastStage,
                        false,
                        reason);
        return new SparseRetrievalOutcome(
                List.of(), preparation, SparseRetrievalFallbackStage.NO_HIT, rewritten, telemetry);
    }

    private List<StageAttempt> buildStageAttempts(SparseQueryPreparation prep) {
        List<StageAttempt> stages = new ArrayList<>();
        LinkedHashSet<String> searchTerms = new LinkedHashSet<>();
        searchTerms.addAll(prep.entityTerms());
        searchTerms.addAll(prep.keywordTerms());
        searchTerms.addAll(prep.synonymTerms());

        for (String phrase : prep.exactPhrases()) {
            if (phrase != null && !phrase.isBlank()) {
                stages.add(
                        new StageAttempt(
                                SparseRetrievalFallbackStage.EXACT_PHRASE,
                                phrase.trim(),
                                TsQueryMode.WEBSEARCH,
                                true));
            }
        }

        for (String term : prioritySingleTerms(prep)) {
            if (term != null && !term.isBlank()) {
                stages.add(
                        new StageAttempt(
                                SparseRetrievalFallbackStage.AND_KEYWORDS,
                                term.trim(),
                                TsQueryMode.WEBSEARCH,
                                true));
            }
        }

        List<String> andTerms = topTerms(searchTerms, 4);
        if (!andTerms.isEmpty()) {
            stages.add(
                    new StageAttempt(
                            SparseRetrievalFallbackStage.AND_KEYWORDS,
                            String.join(" ", andTerms),
                            TsQueryMode.WEBSEARCH,
                            true));
        }

        List<String> orTerms = topTerms(searchTerms, 6);
        if (!orTerms.isEmpty()) {
            stages.add(
                    new StageAttempt(
                            SparseRetrievalFallbackStage.OR_KEYWORDS,
                            joinOrTerms(orTerms),
                            TsQueryMode.OR_TSQUERY,
                            false));
        }

        List<String> unaccentOr = topTerms(unaccentTerms(orTerms), 6);
        if (!unaccentOr.isEmpty()) {
            String orJoined = joinOrTerms(unaccentOr);
            if (!orJoined.equals(joinOrTerms(orTerms))) {
                stages.add(
                        new StageAttempt(
                                SparseRetrievalFallbackStage.UNACCENT_OR,
                                orJoined,
                                TsQueryMode.OR_TSQUERY,
                                false));
            }
        }

        if (Boolean.TRUE.equals(ilikeSupported) && !orTerms.isEmpty()) {
            stages.add(
                    new StageAttempt(
                            SparseRetrievalFallbackStage.ILIKE,
                            joinOrTerms(orTerms),
                            TsQueryMode.ILIKE,
                            false));
        }

        return stages;
    }

    private List<String> prioritySingleTerms(SparseQueryPreparation prep) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String term : prep.keywordTerms()) {
            if (term == null || term.isBlank()) {
                continue;
            }
            String folded = SpanishRetrievalTextSupport.foldAccents(term.toLowerCase(Locale.ROOT).trim());
            if (synonyms.knownHeads().contains(folded)) {
                out.add(term.trim());
            }
        }
        for (String term : prep.synonymTerms()) {
            if (term != null && !term.isBlank()) {
                out.add(term.trim());
            }
        }
        for (String term : prep.keywordTerms()) {
            if (term == null || term.isBlank()) {
                continue;
            }
            if (term.length() >= 5 && !term.chars().allMatch(Character::isDigit)) {
                out.add(term.trim());
            }
            if (out.size() >= 4) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private List<RetrievalCandidate> executeStage(RetrievalRequest req, StageAttempt stage) throws Exception {
        boolean orMode = stage.tsQueryMode() == TsQueryMode.OR_TSQUERY;
        String queryText = SparseTsQuerySanitizer.sanitizeStageQuery(stage.queryText(), orMode);
        if (queryText == null || queryText.isBlank()) {
            log.debug(
                    "Skipping sparse stage {} - query empty or invalid after sanitization (raw='{}')",
                    stage.stage(),
                    truncateForLog(stage.queryText(), 120));
            return List.of();
        }

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("projectId", req.projectId());
        p.addValue("snapshotIds", snapshotIdStrings(req.snapshotIds()));
        p.addValue("limit", req.topKSparse());

        if (stage.tsQueryMode() == TsQueryMode.ILIKE) {
            return executeIlike(req, queryText.startsWith("ILIKE:") ? queryText.substring("ILIKE:".length()) : queryText, p);
        }

        p.addValue("query", queryText);
        String tsqueryExpr = tsqueryExpression(stage.tsQueryMode());
        StringBuilder sql = new StringBuilder(buildSelectClause(tsqueryExpr));
        if (!appendDocumentAllowlist(sql, req, p)) {
            return List.of();
        }
        sql.append(" ORDER BY rank DESC NULLS LAST LIMIT :limit ");

        List<RetrievalCandidate> rows = executeQueryIsolated(sql.toString(), p);
        if (rows.isEmpty() && stage.usePlaintoFallback()) {
            StringBuilder fallbackSql = new StringBuilder(buildSelectClause("plainto_tsquery('simple', :query)"));
            if (!appendDocumentAllowlist(fallbackSql, req, p)) {
                return List.of();
            }
            fallbackSql.append(" ORDER BY rank DESC NULLS LAST LIMIT :limit ");
            rows = executeQueryIsolated(fallbackSql.toString(), p);
        }
        return rows;
    }

    private static String tsqueryExpression(TsQueryMode mode) {
        return switch (mode) {
            case OR_TSQUERY -> "to_tsquery('simple', :query)";
            case PLAINTO -> "plainto_tsquery('simple', :query)";
            case WEBSEARCH -> "websearch_to_tsquery('simple', :query)";
            case ILIKE -> throw new IllegalStateException("ILIKE handled separately");
        };
    }

    private static List<String> snapshotIdStrings(List<UUID> snapshotIds) {
        return snapshotIds.stream().map(UUID::toString).toList();
    }

    private List<RetrievalCandidate> executeIlike(RetrievalRequest req, String orJoined, MapSqlParameterSource p) {
        List<String> terms = splitOrTerms(orJoined);
        if (terms.isEmpty()) {
            return List.of();
        }
        List<String> patterns = new ArrayList<>();
        for (String term : terms) {
            if (term != null && !term.isBlank()) {
                patterns.add("%" + term.trim().toLowerCase(Locale.ROOT) + "%");
            }
        }
        if (patterns.isEmpty()) {
            return List.of();
        }
        StringBuilder likeClause = new StringBuilder(" AND (");
        for (int i = 0; i < patterns.size(); i++) {
            String key = "pattern" + i;
            p.addValue(key, patterns.get(i));
            if (i > 0) {
                likeClause.append(" OR ");
            }
            likeClause.append("lower(content) LIKE :").append(key);
        }
        likeClause.append(") ");

        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT id, content, CAST(metadata AS TEXT) AS metadata_json, chunk_index, 0.01 AS rank
                        FROM vector_store
                        WHERE project_id IS NOT DISTINCT FROM :projectId
                          AND metadata->>'indexSnapshotId' IS NOT NULL
                          AND metadata->>'indexSnapshotId' IN (:snapshotIds)
                        """);
        sql.append(likeClause);
        if (!appendDocumentAllowlist(sql, req, p)) {
            return List.of();
        }
        sql.append(" LIMIT :limit ");
        return executeQueryIsolated(sql.toString(), p);
    }

    private String buildSelectClause(String tsqueryExpr) {
        return """
                SELECT id, content, CAST(metadata AS TEXT) AS metadata_json, chunk_index,
                  ts_rank_cd(content_tsv, %s) AS rank
                FROM vector_store
                WHERE project_id IS NOT DISTINCT FROM :projectId
                  AND metadata->>'indexSnapshotId' IS NOT NULL
                  AND metadata->>'indexSnapshotId' IN (:snapshotIds)
                  AND content_tsv @@ %s
                """
                .formatted(tsqueryExpr, tsqueryExpr);
    }

    private boolean appendDocumentAllowlist(StringBuilder sql, RetrievalRequest req, MapSqlParameterSource p) {
        if (req.documentAllowlistIsAll()) {
            return true;
        }
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
            return false;
        }
        p.addValue("docIds", docUuids.stream().map(UUID::toString).toList());
        sql.append(
                """
                 AND (
                   metadata->>'documentId' IN (:docIds)
                   OR metadata->>'projectDocumentId' IN (:docIds)
                 )
                """);
        return true;
    }

    private List<RetrievalCandidate> executeQueryIsolated(String sql, MapSqlParameterSource p) {
        if (transactionTemplate == null) {
            return executeQuery(sql, p);
        }
        if (!savepointsSupported) {
            return executeQueryInNestedTransaction(sql, p);
        }
        return transactionTemplate.execute(
                status -> {
                    try {
                        Object savepoint = status.createSavepoint();
                        try {
                            List<RetrievalCandidate> rows = executeQuery(sql, p);
                            status.releaseSavepoint(savepoint);
                            return rows;
                        } catch (DataAccessException ex) {
                            status.rollbackToSavepoint(savepoint);
                            status.releaseSavepoint(savepoint);
                            throw ex;
                        }
                    } catch (TransactionException ex) {
                        log.warn(
                                "Sparse retrieval savepoint failed - falling back to nested transaction: {}",
                                ex.getMessage());
                        return executeQueryInNestedTransaction(sql, p);
                    }
                });
    }

    /**
     * Runs sparse SQL in a separate transaction so failures do not mark the chat transaction
     * rollback-only when JPA savepoints are unavailable.
     */
    private List<RetrievalCandidate> executeQueryInNestedTransaction(String sql, MapSqlParameterSource p) {
        if (nestedTransactionTemplate == null) {
            return executeQuery(sql, p);
        }
        return nestedTransactionTemplate.execute(
                status -> {
                    try {
                        return executeQuery(sql, p);
                    } catch (DataAccessException ex) {
                        status.setRollbackOnly();
                        throw ex;
                    }
                });
    }

    private static boolean probeSavepointSupport(PlatformTransactionManager transactionManager) {
        if (transactionManager == null) {
            return false;
        }
        TransactionTemplate probe = new TransactionTemplate(transactionManager);
        try {
            Boolean supported =
                    probe.execute(
                            status -> {
                                try {
                                    Object savepoint = status.createSavepoint();
                                    status.releaseSavepoint(savepoint);
                                    return true;
                                } catch (TransactionException ex) {
                                    return false;
                                }
                            });
            return Boolean.TRUE.equals(supported);
        } catch (Exception ex) {
            return false;
        }
    }

    private List<RetrievalCandidate> executeQuery(String sql, MapSqlParameterSource p) {
        List<RetrievalCandidate> rows =
                jdbc.query(
                        sql,
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
                            Map<String, Object> tagged =
                                    FusionOriginSupport.withRetrievalOrigin(meta, "SPARSE");
                            return new RetrievalCandidate(
                                    cid,
                                    rs.getString("content") != null ? rs.getString("content") : "",
                                    tagged,
                                    Double.NaN,
                                    rank,
                                    0,
                                    sparseRank,
                                    snapshotId,
                                    rrfProxy);
                        });
        return rows.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    private Map<String, Object> parseMetadata(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metaJson, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static Boolean probeIlikeSupport(NamedParameterJdbcTemplate jdbc) {
        try {
            Integer one = jdbc.getJdbcOperations().queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> topTerms(Iterable<String> terms, int max) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (terms == null) {
            return List.of();
        }
        for (String t : terms) {
            if (t == null || t.isBlank()) {
                continue;
            }
            out.add(t.trim());
            if (out.size() >= max) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private static List<String> unaccentTerms(List<String> terms) {
        List<String> out = new ArrayList<>();
        for (String t : terms) {
            out.add(SpanishRetrievalTextSupport.foldAccents(t.toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    private static String joinOrTerms(List<String> terms) {
        return SparseTsQuerySanitizer.joinOrTerms(terms);
    }

    private static String truncateForLog(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    private static List<String> splitOrTerms(String orJoined) {
        if (orJoined == null || orJoined.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : orJoined.split("\\|")) {
            if (part != null && !part.isBlank()) {
                out.add(part.trim().replace(" & ", " "));
            }
        }
        return out;
    }

    private static String lastAttemptQuery(SparseQueryPreparation prep) {
        List<String> terms = topTerms(prep.keywordTerms(), 4);
        return terms.isEmpty() ? prep.normalizedQuery() : String.join(" | ", terms);
    }

    private enum TsQueryMode {
        WEBSEARCH,
        PLAINTO,
        OR_TSQUERY,
        ILIKE
    }

    private record StageAttempt(
            SparseRetrievalFallbackStage stage,
            String queryText,
            TsQueryMode tsQueryMode,
            boolean usePlaintoFallback) {
        StageAttempt {
            if (stage == SparseRetrievalFallbackStage.ILIKE) {
                queryText = "ILIKE:" + queryText;
                tsQueryMode = TsQueryMode.ILIKE;
            }
        }
    }
}
