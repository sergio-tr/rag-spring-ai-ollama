package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.result.chat.ChatSource;
import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.application.result.evaluation.LlmJudgeItemResult;
import com.uniovi.rag.application.service.evaluation.preset.TypedRagPresetBenchmarkOrchestrator;
import com.uniovi.rag.domain.model.QueryType;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class P3RetrievalPersistenceTest {

    @Test
    void mergeQueryResponseTelemetry_persistsSourcesAndRetrievedIds() throws Exception {
        Map<String, Object> tel = new LinkedHashMap<>();
        tel.put("workflowName", "ChunkDenseRagWorkflow");
        tel.put("sourceCount", 1);
        tel.put("promptContextCharCount", 240);
        tel.put("retrievalDenseCandidateCount", 1);

        ChatSource source =
                new ChatSource(
                        "doc-hash",
                        "proj-doc",
                        "acta.pdf",
                        "snippet",
                        0.1,
                        "distance",
                        2,
                        null,
                        Map.of("chunkId", "snap:doc:2"));

        QueryResponse response =
                QueryResponse.fromLLMWithSources("answer", QueryType.GET_FIELD, List.of(source), tel);

        Map<String, Object> merged = invokeMergeTelemetry(response);

        assertThat(merged.get("sourceCount")).isEqualTo(1);
        assertThat(merged.get("sources")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) merged.get("sources");
        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().get("chunkId")).isEqualTo("snap:doc:2");
        assertThat(merged.get("retrieved_chunk_ids")).isEqualTo(List.of("snap:doc:2"));
        assertThat(merged.get("retrieved_document_ids")).isEqualTo(List.of("doc-hash"));
    }

    @Test
    void enrichRows_mergesTelemetryIntoMetricsPayload() throws Exception {
        LlmJudgeItemResult item =
                LlmJudgeItemResult.builder()
                        .question("q")
                        .chatTelemetry(
                                Map.of(
                                        "sourceCount",
                                        1,
                                        "retrievalDenseCandidateCount",
                                        1,
                                        "contextChunkCount",
                                        1,
                                        "promptContextCharCount",
                                        200,
                                        "retrieved_chunk_ids",
                                        List.of("snap:1:0")))
                        .build();

        Map<String, Object> row = new LinkedHashMap<>(EvaluationPayloadMapper.toRowMap(item));
        row.put(AbstractEvaluationService.EVALUATION_CHAT_TELEMETRY_ROW_KEY, item.chatTelemetry());
        Map<String, Object> metrics = new LinkedHashMap<>();
        invokeOrchestratorTelemetryMerge(row, metrics);

        assertThat(metrics.get("sourceCount")).isEqualTo(1);
        assertThat(metrics.get("retrievalDenseCandidateCount")).isEqualTo(1);
        assertThat(metrics.get("contextChunkCount")).isEqualTo(1);
        assertThat(metrics.get("promptContextCharCount")).isEqualTo(200);
        assertThat(metrics.get("retrieved_chunk_ids")).isEqualTo(List.of("snap:1:0"));
    }

    @Test
    void canonicalPersistence_retainsRetrievalMetricsInMetricsPayload() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("question", "q");
        row.put("correct_answer", "a");
        row.put("generated_answer", "g");
        row.put("llm_evaluation", "");
        row.put(
                "metrics_payload",
                Map.of(
                        "sourceCount",
                        1,
                        "retrievalDenseCandidateCount",
                        1,
                        "retrieved_chunk_ids",
                        List.of("snap:doc:0"),
                        "promptContextCharCount",
                        150));

        Map<String, Object> metrics = new LinkedHashMap<>();
        invokeCanonicalMetricsMerge(row, metrics);

        assertThat(metrics.get("sourceCount")).isEqualTo(1);
        assertThat(metrics.get("retrieved_chunk_ids")).isEqualTo(List.of("snap:doc:0"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeMergeTelemetry(QueryResponse response) throws Exception {
        Method m =
                AbstractEvaluationService.class.getDeclaredMethod("mergeQueryResponseTelemetry", QueryResponse.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(null, response);
    }

    private static void invokeOrchestratorTelemetryMerge(Map<String, Object> row, Map<String, Object> metrics)
            throws Exception {
        Method m =
                TypedRagPresetBenchmarkOrchestrator.class.getDeclaredMethod(
                        "mergeEvaluationTelemetryIntoMetrics", Map.class, Map.class);
        m.setAccessible(true);
        m.invoke(null, row, metrics);
    }

    private static void invokeCanonicalMetricsMerge(Map<String, Object> row, Map<String, Object> metrics)
            throws Exception {
        Method m =
                EvaluationCanonicalPersistenceService.class.getDeclaredMethod(
                        "mergeMetricsPayloadFromRow", Map.class, Map.class);
        m.setAccessible(true);
        m.invoke(null, row, metrics);
    }
}
