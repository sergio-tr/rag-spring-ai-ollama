package com.uniovi.rag.service.evaluation.mvp;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkMvpMetricsCalculatorTest {

    @Test
    void normalizedExactMatch_ignoreCaseAndWhitespace() {
        assertThat(BenchmarkMvpMetricsCalculator.normalizedExactMatch("  Hello\nWorld ", "hello world"))
                .isTrue();
    }

    @Test
    void containsExpectedAnswer_substringNormalized() {
        assertThat(BenchmarkMvpMetricsCalculator.containsExpectedAnswer("Paris", "The answer is paris today."))
                .isTrue();
    }

    @Test
    void embeddingRow_handChecked_retrievalAndOperational() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setId(UUID.randomUUID());
        e.setBenchmarkKind(BenchmarkKind.EMBEDDING_RETRIEVAL.name());
        e.setQueryType("FACTOID");
        e.setExpectedAnswer("Acta 12");
        e.setActualAnswer("top chunk id");
        e.setLatencyMs(99L);
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put("recall_at_1", 0.0);
        mp.put("recall_at_3", 1.0);
        mp.put("recall_at_5", 1.0);
        mp.put("mrr", 0.5);
        mp.put("retrieved_count", 5);
        mp.put("gold_found", true);
        mp.put(BenchmarkResultRowKeys.DIFFICULTY, "EASY");
        mp.put(BenchmarkResultRowKeys.DATASET_QUESTION_ID, "q1");
        e.setMetricsPayload(mp);

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setLlmModelId("run-llm");
        run.setEmbeddingModelId("run-emb");

        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, run);
        @SuppressWarnings("unchecked")
        Map<String, Object> ret = (Map<String, Object>) mvp.get("retrieval");
        assertThat(ret.get("applicable")).isEqualTo(true);
        assertThat(ret.get("recallAt3")).isEqualTo(1.0);
        assertThat(ret.get("recallAt5")).isEqualTo(1.0);
        assertThat(ret.get("mrr")).isEqualTo(0.5);
        assertThat(ret.get("retrievedCount")).isEqualTo(5);
        assertThat(ret.get("goldFound")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> gen = (Map<String, Object>) mvp.get("generation");
        assertThat(gen.get("answerLength")).isEqualTo("top chunk id".length());
        assertThat(gen.get("semanticScore")).isEqualTo(BenchmarkMvpSchema.NOT_AVAILABLE);

        @SuppressWarnings("unchecked")
        Map<String, Object> op = (Map<String, Object>) mvp.get("operational");
        assertThat(op.get("latencyMs")).isEqualTo(99L);
        assertThat(op.get("embeddingModelId")).isEqualTo("run-emb");
        assertThat(op.get("failureCode")).isEqualTo("");
        assertThat(op.get("unsupportedReason")).isEqualTo("");
    }

    @Test
    void ragRow_retrievalMarkedNotAvailable_semanticFromJudge() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setExpectedAnswer("yes");
        e.setActualAnswer("Yes.");
        e.setLatencyMs(10L);
        Map<String, Object> judge = Map.of("correctness", 5);
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put("judge_scores", judge);
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P2");
        mp.put(BenchmarkResultRowKeys.LLM_MODEL_ID, "m1");
        mp.put(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID, "e1");
        e.setMetricsPayload(mp);

        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> ret = (Map<String, Object>) mvp.get("retrieval");
        assertThat(ret.get("recallAt1")).isEqualTo(BenchmarkMvpSchema.NOT_AVAILABLE);

        @SuppressWarnings("unchecked")
        Map<String, Object> gen = (Map<String, Object>) mvp.get("generation");
        assertThat(gen.get("normalizedExactMatch")).isEqualTo(false);
        assertThat(gen.get("semanticScore")).isEqualTo(1.0);
    }

    @Test
    void notSupportedRow_setsUnsupportedReasonFromErrorCode() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setExpectedAnswer("x");
        e.setActualAnswer("");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.NOT_SUPPORTED.name());
        mp.put(BenchmarkResultRowKeys.ERROR_CODE, "PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED");
        e.setMetricsPayload(mp);

        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> op = (Map<String, Object>) mvp.get("operational");
        assertThat(op.get("unsupportedReason")).isEqualTo("PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED");
    }

    @Test
    void deriveRecallFromRank_whenRecallAt3Missing() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.EMBEDDING_RETRIEVAL.name());
        e.setMetricsPayload(
                Map.of(
                        BenchmarkResultRowKeys.ITEM_OUTCOME,
                        BenchmarkItemOutcome.EXECUTED.name(),
                        "recall_at_1",
                        0.0,
                        "first_relevant_rank",
                        2,
                        "retrieved_count",
                        10,
                        "mrr",
                        0.5));
        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> ret = (Map<String, Object>) mvp.get("retrieval");
        assertThat(ret.get("recallAt3")).isEqualTo(1.0);
        assertThat(ret.get("recallAt5")).isEqualTo(1.0);
    }
}
