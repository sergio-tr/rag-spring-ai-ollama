package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingRetrievalQuery;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Resolves and applies dataset question subset filters for benchmark runs. */
public final class DatasetQuestionSubsetSupport {

    public static final String AGG_KEY_SUBSET_ID = "subsetId";
    public static final String AGG_KEY_SUBSET_NAME = "subsetName";
    public static final String AGG_KEY_SUBSET_VERSION = "subsetVersion";
    public static final String AGG_KEY_FILTERED_QUESTION_IDS = "filteredQuestionIds";
    public static final String AGG_KEY_DATASET_QUESTION_FILTER = "datasetQuestionFilter";
    public static final String AGG_KEY_FILTERED_QUESTION_COUNT = "filteredQuestionCount";
    public static final String AGG_KEY_ROUTING_ORACLE_ENABLED = "routingQueryTypeOracleEnabled";

    public static final String FILTER_NONE = "NONE";
    public static final String FILTER_EXPLICIT_IDS = "EXPLICIT_IDS";
    public static final String FILTER_GOLD_SUBSET = "GOLD_SUBSET";

    private DatasetQuestionSubsetSupport() {}

    public static boolean hasSubset(StartBenchmarkRunRequest request) {
        return request != null
                && (request.goldSubsetManifestId() != null && !request.goldSubsetManifestId().isBlank()
                        || !request.datasetQuestionIds().isEmpty());
    }

    public static Integer resolvedItemCount(StartBenchmarkRunRequest request) {
        if (request == null) {
            return null;
        }
        if (request.goldSubsetManifestId() != null && !request.goldSubsetManifestId().isBlank()) {
            return GoldSubsetManifestLoader.load(request.goldSubsetManifestId()).entries().size();
        }
        if (!request.datasetQuestionIds().isEmpty()) {
            return request.datasetQuestionIds().size();
        }
        return null;
    }

    /**
     * Planned workload for validation: subset-filtered question count × preset count when a subset is active,
     * otherwise the full dataset question count × preset count.
     */
    public static int resolvedExpectedItemCount(
            Map<String, Object> aggregates, int fullQuestionCount, int selectedPresetCount) {
        int presets = Math.max(1, selectedPresetCount);
        Integer subsetQuestions = readFilteredQuestionCount(aggregates);
        if (subsetQuestions != null && subsetQuestions > 0) {
            return subsetQuestions * presets;
        }
        return Math.max(0, fullQuestionCount) * presets;
    }

    private static Integer readFilteredQuestionCount(Map<String, Object> aggregates) {
        if (aggregates == null || aggregates.isEmpty()) {
            return null;
        }
        Object filtered = aggregates.get(AGG_KEY_FILTERED_QUESTION_COUNT);
        if (filtered instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        Object filterRaw = aggregates.get(AGG_KEY_DATASET_QUESTION_FILTER);
        if (filterRaw == null || FILTER_NONE.equals(String.valueOf(filterRaw))) {
            return null;
        }
        List<String> ids = readStringList(aggregates.get(AGG_KEY_FILTERED_QUESTION_IDS));
        return ids.isEmpty() ? null : ids.size();
    }

    public static ResolvedSubset resolve(StartBenchmarkRunRequest request) {
        if (request == null) {
            return ResolvedSubset.all();
        }
        if (request.goldSubsetManifestId() != null && !request.goldSubsetManifestId().isBlank()) {
            GoldSubsetManifest manifest = GoldSubsetManifestLoader.load(request.goldSubsetManifestId());
            return new ResolvedSubset(
                    FILTER_GOLD_SUBSET,
                    manifest.manifestId(),
                    manifest.manifestId(),
                    manifest.manifestVersion(),
                    List.copyOf(manifest.questionIds()));
        }
        if (!request.datasetQuestionIds().isEmpty()) {
            List<String> ids =
                    request.datasetQuestionIds().stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .distinct()
                            .toList();
            return new ResolvedSubset(FILTER_EXPLICIT_IDS, null, null, null, ids);
        }
        return ResolvedSubset.all();
    }

    public static Optional<ResolvedSubset> readFromRun(EvaluationRunEntity run) {
        if (run == null || run.getAggregatesJson() == null || run.getAggregatesJson().isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> agg = run.getAggregatesJson();
        Object filterRaw = agg.get(AGG_KEY_DATASET_QUESTION_FILTER);
        if (filterRaw == null || FILTER_NONE.equals(String.valueOf(filterRaw))) {
            return Optional.empty();
        }
        List<String> ids = readStringList(agg.get(AGG_KEY_FILTERED_QUESTION_IDS));
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                new ResolvedSubset(
                        String.valueOf(filterRaw),
                        str(agg.get(AGG_KEY_SUBSET_ID)),
                        str(agg.get(AGG_KEY_SUBSET_NAME)),
                        str(agg.get(AGG_KEY_SUBSET_VERSION)),
                        ids));
    }

    public static List<RagPresetQuestion> filterQuestions(List<RagPresetQuestion> questions, ResolvedSubset subset) {
        return filterByQuestionId(
                questions,
                subset,
                RagPresetQuestion::id,
                id -> "Unknown dataset question id: " + id);
    }

    public static List<LlmReaderQuestion> filterLlmQuestions(
            List<LlmReaderQuestion> questions, ResolvedSubset subset) {
        return filterByQuestionId(
                questions,
                subset,
                LlmReaderQuestion::id,
                id -> "Unknown llm_reader_questions id: " + id);
    }

    public static List<EmbeddingRetrievalQuery> filterEmbeddingQueries(
            List<EmbeddingRetrievalQuery> queries, ResolvedSubset subset) {
        return filterByQuestionId(
                queries,
                subset,
                EmbeddingRetrievalQuery::id,
                id -> "Unknown embedding_retrieval_queries id: " + id);
    }

    private static <T> List<T> filterByQuestionId(
            List<T> items,
            ResolvedSubset subset,
            java.util.function.Function<T, String> idFn,
            java.util.function.Function<String, String> unknownIdMessage) {
        if (items == null || items.isEmpty() || subset == null || subset.allQuestions()) {
            return items != null ? items : List.of();
        }
        Map<String, T> byId = new LinkedHashMap<>();
        for (T item : items) {
            if (item != null) {
                byId.put(idFn.apply(item), item);
            }
        }
        List<T> filtered = new ArrayList<>();
        for (String id : subset.questionIds()) {
            T item = byId.get(id);
            if (item == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, unknownIdMessage.apply(id));
            }
            filtered.add(item);
        }
        return List.copyOf(filtered);
    }

    public static void applyToAggregates(Map<String, Object> agg, StartBenchmarkRunRequest request) {
        if (agg == null || request == null) {
            return;
        }
        if (request.routingQueryTypeOracleEnabledEffective()) {
            agg.put(AGG_KEY_ROUTING_ORACLE_ENABLED, true);
        }
        if (!hasSubset(request)) {
            return;
        }
        ResolvedSubset subset = resolve(request);
        if (subset.allQuestions()) {
            return;
        }
        agg.put(AGG_KEY_DATASET_QUESTION_FILTER, subset.filterMode());
        agg.put(AGG_KEY_FILTERED_QUESTION_IDS, subset.questionIds());
        agg.put(AGG_KEY_FILTERED_QUESTION_COUNT, subset.questionIds().size());
        if (subset.subsetId() != null && !subset.subsetId().isBlank()) {
            agg.put(AGG_KEY_SUBSET_ID, subset.subsetId());
            agg.put(AGG_KEY_SUBSET_NAME, subset.subsetName());
            agg.put(AGG_KEY_SUBSET_VERSION, subset.subsetVersion());
        }
    }

    public static boolean routingOracleEnabled(EvaluationRunEntity run) {
        if (run == null || run.getAggregatesJson() == null || run.getAggregatesJson().isEmpty()) {
            return false;
        }
        Object raw = run.getAggregatesJson().get(AGG_KEY_ROUTING_ORACLE_ENABLED);
        return Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw));
    }

    public static void copySubsetMetadataToMetrics(Map<String, Object> metrics, ResolvedSubset subset) {
        if (metrics == null || subset == null || subset.allQuestions()) {
            return;
        }
        if (subset.subsetId() != null && !subset.subsetId().isBlank()) {
            metrics.put("subsetId", subset.subsetId());
            metrics.put("subsetName", subset.subsetName());
            metrics.put("subsetVersion", subset.subsetVersion());
        }
        if (subset.filterMode() != null && !subset.filterMode().isBlank()) {
            metrics.put(AGG_KEY_DATASET_QUESTION_FILTER, subset.filterMode());
        }
    }

    /**
     * Applies authoritative answerability from a gold-subset manifest entry when the workbook row lacks
     * declared unanswerable/ambiguous columns.
     */
    public static boolean enrichAnswerabilityFromGoldManifest(
            Map<String, Object> metrics, String datasetQuestionId, String manifestId) {
        if (metrics == null
                || datasetQuestionId == null
                || datasetQuestionId.isBlank()
                || manifestId == null
                || manifestId.isBlank()) {
            return false;
        }
        GoldSubsetManifest manifest = GoldSubsetManifestLoader.load(manifestId);
        for (GoldSubsetManifest.Entry entry : manifest.entries()) {
            if (!datasetQuestionId.equals(entry.datasetQuestionId())) {
                continue;
            }
            if (entry.answerability() == null || entry.answerability().isBlank()) {
                return false;
            }
            Answerability answerability;
            try {
                answerability = Answerability.valueOf(entry.answerability().trim());
            } catch (IllegalArgumentException ex) {
                return false;
            }
            AnswerabilityLabelResult label =
                    new AnswerabilityLabelResult(
                            answerability,
                            AnswerabilitySource.GOLD_SUBSET_MANIFEST,
                            "gold_subset:" + manifestId,
                            AnswerabilityLabelConfidence.HIGH,
                            "gold_subset_entry");
            DatasetMetricContract.applyLabelResult(metrics, label, manifest.labelledDatasetSha256());
            if (entry.errorCategory() != null && !entry.errorCategory().isBlank()) {
                metrics.put("goldErrorCategory", entry.errorCategory());
            }
            return true;
        }
        return false;
    }

    /** Export-time enrichment when persisted metrics lack answerability but carry gold-subset metadata. */
    public static boolean enrichAnswerabilityFromPersistedSubset(
            Map<String, Object> metrics, String datasetQuestionId) {
        if (metrics == null
                || datasetQuestionId == null
                || datasetQuestionId.isBlank()
                || DatasetMetricContract.readAnswerability(metrics) != Answerability.UNKNOWN) {
            return false;
        }
        String subsetId = str(metrics.get("subsetId"));
        if (subsetId == null || subsetId.isBlank()) {
            return false;
        }
        return enrichAnswerabilityFromGoldManifest(metrics, datasetQuestionId, subsetId);
    }

    public static void copySubsetMetadataFromRun(Map<String, Object> metrics, EvaluationRunEntity run) {
        readFromRun(run).ifPresent(subset -> copySubsetMetadataToMetrics(metrics, subset));
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    String s = String.valueOf(item).trim();
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
            return List.copyOf(out);
        }
        return List.of();
    }

    private static String str(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    public record ResolvedSubset(
            String filterMode, String subsetId, String subsetName, String subsetVersion, List<String> questionIds) {

        public ResolvedSubset {
            questionIds = questionIds != null ? List.copyOf(questionIds) : List.of();
        }

        public boolean allQuestions() {
            return questionIds.isEmpty() || FILTER_NONE.equals(filterMode);
        }

        public static ResolvedSubset all() {
            return new ResolvedSubset(FILTER_NONE, null, null, null, List.of());
        }
    }
}
