package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists canonical benchmark rows into {@code evaluation_result} and run aggregates.
 */
@Service
public class EvaluationCanonicalPersistenceService {

    private static final String JSON_KEY_CORRECT_ANSWER = "correct_answer";

    private final EvaluationRunRepository evaluationRunRepository;
    private final EvaluationResultRepository evaluationResultRepository;

    private final boolean persistenceEnabled;

    public EvaluationCanonicalPersistenceService(
            EvaluationRunRepository evaluationRunRepository,
            EvaluationResultRepository evaluationResultRepository,
            @Value("${rag.evaluation.persistence.enabled:true}") boolean persistenceEnabled) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.evaluationResultRepository = evaluationResultRepository;
        this.persistenceEnabled = persistenceEnabled;
    }

    @Transactional
    public void markRunFailed(UUID runId, String message) {
        if (!persistenceEnabled || runId == null) {
            return;
        }
        EvaluationRunEntity run = evaluationRunRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        run.setStatus(EvaluationRunStatus.ERROR);
        run.setCompletedAt(Instant.now());
        Map<String, Object> agg = run.getAggregatesJson() != null
                ? new LinkedHashMap<>(run.getAggregatesJson())
                : new LinkedHashMap<>();
        agg.put("error", message != null ? message : "unknown");
        run.setAggregatesJson(agg);
        evaluationRunRepository.save(run);
    }

    @Transactional
    public void markRunCancelled(UUID runId, String reason) {
        if (!persistenceEnabled || runId == null) {
            return;
        }
        EvaluationRunEntity run = evaluationRunRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        run.setStatus(EvaluationRunStatus.ERROR);
        run.setCompletedAt(Instant.now());
        Map<String, Object> agg = run.getAggregatesJson() != null
                ? new LinkedHashMap<>(run.getAggregatesJson())
                : new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) {
            agg.put("cancelled", reason);
        } else {
            agg.put("cancelled", true);
        }
        run.setAggregatesJson(agg);
        evaluationRunRepository.save(run);
    }

    @Transactional
    public void persistLlmJudgeBatch(UUID runId, LlmJudgeEvaluationBatchResult batch, BenchmarkKind kind) {
        if (batch == null) {
            return;
        }
        persistLlmJudgeFromEvaluationMap(runId, EvaluationPayloadMapper.toAsyncPayload(batch), kind);
    }

    @Transactional
    public void persistLlmJudgeFromEvaluationMap(UUID runId, Map<String, Object> evaluationPayload, BenchmarkKind kind) {
        if (!persistenceEnabled || runId == null || evaluationPayload == null) {
            return;
        }
        EvaluationRunEntity run = evaluationRunRepository.findById(runId).orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) evaluationPayload.get("results");
        if (rows == null) {
            rows = List.of();
        }
        String kindName = kind.name();
        Instant now = Instant.now();
        List<EvaluationResultEntity> saved = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            EvaluationResultEntity e = new EvaluationResultEntity();
            e.setRun(run);
            e.setQuestionText(str(r.get("question")));
            e.setExpectedAnswer(str(r.get(JSON_KEY_CORRECT_ANSWER)));
            e.setActualAnswer(str(r.get("generated_answer")));
            e.setQueryType(str(r.get("query_type")));
            e.setEvaluatedAt(now);
            e.setBenchmarkKind(kindName);
            String evalText = str(r.get("llm_evaluation"));
            Map<String, Integer> scores = JudgeScoreParser.parseScores(evalText);
            Integer correctness = scores.get("correctness");
            e.setCorrectness(correctness);
            Object latMs = r.get(BenchmarkResultRowKeys.LATENCY_MS);
            if (latMs instanceof Number n) {
                e.setLatencyMs(n.longValue());
            }
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("judge_scores", scores);
            metrics.put("llm_evaluation_excerpt", trunc(evalText, 4000));
            mergeOptionalRowKeys(r, metrics);
            mergeMetricsPayloadFromRow(r, metrics);
            e.setMetricsPayload(metrics);
            saved.add(e);
        }
        evaluationResultRepository.saveAll(saved);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) evaluationPayload.get("evaluation_summary");
        Map<String, Object> merged = new LinkedHashMap<>();
        if (run.getAggregatesJson() != null && !run.getAggregatesJson().isEmpty()) {
            merged.putAll(run.getAggregatesJson());
        }
        if (summary != null && !summary.isEmpty()) {
            merged.putAll(summary);
        }
        run.setAggregatesJson(merged.isEmpty() ? Map.of() : Map.copyOf(merged));
        if (summary != null && Boolean.TRUE.equals(summary.get("cancelled"))) {
            run.setStatus(EvaluationRunStatus.ERROR);
            Map<String, Object> agg = run.getAggregatesJson() != null ? new LinkedHashMap<>(run.getAggregatesJson()) : new LinkedHashMap<>();
            agg.put("partialCancelled", true);
            run.setAggregatesJson(agg);
        } else {
            run.setStatus(EvaluationRunStatus.DONE);
        }
        run.setProgress(100);
        run.setCompletedAt(Instant.now());
        evaluationRunRepository.save(run);
    }

    @Transactional
    public void persistEmbeddingRetrievalResults(UUID runId, Map<String, Object> payload) {
        if (!persistenceEnabled || runId == null || payload == null) {
            return;
        }
        EvaluationRunEntity run = evaluationRunRepository.findById(runId).orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("results");
        if (rows == null) {
            rows = List.of();
        }
        Instant now = Instant.now();
        List<EvaluationResultEntity> saved = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            EvaluationResultEntity e = new EvaluationResultEntity();
            e.setRun(run);
            e.setQuestionText(str(r.get("question")));
            e.setExpectedAnswer(str(r.get("expected_answer")));
            String generated = str(r.get("generated_answer"));
            e.setActualAnswer(
                    generated != null && !generated.isBlank()
                            ? generated
                            : str(r.get("top_document_id")));
            Object lat = r.get("latency_ms");
            e.setLatencyMs(lat instanceof Number n ? n.longValue() : null);
            e.setEvaluatedAt(now);
            e.setBenchmarkKind(BenchmarkKind.EMBEDDING_RETRIEVAL.name());
            Map<String, Object> metrics = shallowCopyStringKeyed(r.get("metrics"));
            mergeOptionalRowKeys(r, metrics);
            mergeMetricsPayloadFromRow(r, metrics);
            String evalText = str(r.get("llm_evaluation"));
            if (evalText != null && !evalText.isBlank()) {
                Map<String, Integer> scores = JudgeScoreParser.parseScores(evalText);
                e.setCorrectness(scores.get("correctness"));
                metrics.put("judge_scores", scores);
                metrics.put("llm_evaluation_excerpt", trunc(evalText, 4000));
            }
            e.setMetricsPayload(metrics);
            saved.add(e);
        }
        evaluationResultRepository.saveAll(saved);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) payload.get("evaluation_summary");
        run.setAggregatesJson(summary != null ? new LinkedHashMap<>(summary) : Map.of());
        if (summary != null && Boolean.TRUE.equals(summary.get("cancelled"))) {
            run.setStatus(EvaluationRunStatus.ERROR);
            Map<String, Object> agg = run.getAggregatesJson() != null ? new LinkedHashMap<>(run.getAggregatesJson()) : new LinkedHashMap<>();
            agg.put("partialCancelled", true);
            run.setAggregatesJson(agg);
        } else {
            run.setStatus(EvaluationRunStatus.DONE);
        }
        run.setProgress(100);
        run.setCompletedAt(Instant.now());
        evaluationRunRepository.save(run);
    }

    @Transactional
    public void persistClassifierMetrics(UUID runId, Map<String, Object> classifierResponse) {
        if (!persistenceEnabled || runId == null || classifierResponse == null) {
            return;
        }
        EvaluationRunEntity run = evaluationRunRepository.findById(runId).orElseThrow();
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setRun(run);
        e.setQuestionText("CLASSIFIER_EVAL_AGGREGATE");
        e.setEvaluatedAt(Instant.now());
        e.setBenchmarkKind(BenchmarkKind.CLASSIFIER_METRICS.name());
        e.setMetricsPayload(new LinkedHashMap<>(classifierResponse));
        evaluationResultRepository.save(e);
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("classifier", new LinkedHashMap<>(classifierResponse));
        run.setAggregatesJson(agg);
        run.setStatus(EvaluationRunStatus.DONE);
        run.setProgress(100);
        run.setCompletedAt(Instant.now());
        evaluationRunRepository.save(run);
    }

    /**
     * Overlay row-level {@code metrics_payload} (Lab index plan / gate payload) onto persisted metrics so exports see the same keys as the runner output.
     */
    private static void mergeMetricsPayloadFromRow(Map<String, Object> row, Map<String, Object> metrics) {
        Object mp = row.get("metrics_payload");
        if (!(mp instanceof Map<?, ?> mm)) {
            return;
        }
        for (Map.Entry<?, ?> e : mm.entrySet()) {
            if (e.getKey() != null) {
                metrics.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
    }

    private static void mergeOptionalRowKeys(Map<String, Object> row, Map<String, Object> metrics) {
        Object qid = row.get(BenchmarkResultRowKeys.DATASET_QUESTION_ID);
        if (qid != null) {
            metrics.put(BenchmarkResultRowKeys.DATASET_QUESTION_ID, qid);
        }
        Object outcome = row.get(BenchmarkResultRowKeys.ITEM_OUTCOME);
        if (outcome != null) {
            metrics.put(BenchmarkResultRowKeys.ITEM_OUTCOME, outcome);
        }
        Object err = row.get("error");
        if (err != null) {
            metrics.put("error", err);
        }
        Object bp = row.get("benchmark_protocol");
        if (bp != null) {
            metrics.put("benchmark_protocol", bp);
        }
        Object bm = row.get("baseline_metrics");
        if (bm instanceof Map<?, ?> mm) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : mm.entrySet()) {
                if (e.getKey() != null) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            metrics.put("baseline_metrics", copy);
        }
        Object preset = row.get(BenchmarkResultRowKeys.PRESET_CODE);
        if (preset != null) {
            metrics.put(BenchmarkResultRowKeys.PRESET_CODE, preset);
        }
        Object presetLabel = row.get(BenchmarkResultRowKeys.PRESET_LABEL);
        if (presetLabel != null) {
            metrics.put(BenchmarkResultRowKeys.PRESET_LABEL, presetLabel);
        }
        Object difficulty = row.get(BenchmarkResultRowKeys.DIFFICULTY);
        if (difficulty != null) {
            metrics.put(BenchmarkResultRowKeys.DIFFICULTY, difficulty);
        }
        Object latency = row.get(BenchmarkResultRowKeys.LATENCY_MS);
        if (latency != null) {
            metrics.put(BenchmarkResultRowKeys.LATENCY_MS, latency);
        }
        Object errCode = row.get(BenchmarkResultRowKeys.ERROR_CODE);
        if (errCode != null) {
            metrics.put(BenchmarkResultRowKeys.ERROR_CODE, errCode);
        }
        Object reason = row.get(BenchmarkResultRowKeys.REASON);
        if (reason != null) {
            metrics.put(BenchmarkResultRowKeys.REASON, reason);
        }
        Object llmMid = row.get(BenchmarkResultRowKeys.LLM_MODEL_ID);
        if (llmMid != null) {
            metrics.put(BenchmarkResultRowKeys.LLM_MODEL_ID, llmMid);
        }
        Object embMid = row.get(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID);
        if (embMid != null) {
            metrics.put(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID, embMid);
        }
    }

    private static Map<String, Object> shallowCopyStringKeyed(Object rawMetrics) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!(rawMetrics instanceof Map<?, ?> m)) {
            return out;
        }
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String trunc(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
