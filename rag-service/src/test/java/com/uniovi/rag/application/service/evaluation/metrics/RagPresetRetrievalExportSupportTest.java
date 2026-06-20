package com.uniovi.rag.application.service.evaluation.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.evaluation.metrics.matching.ExpectedAnswerMatchResult;
import com.uniovi.rag.infrastructure.persistence.evaluation.LabCampaignHumanExportBuilder;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RagPresetRetrievalExportSupportTest {

    @Test
    void putJsonExportFields_promotesP3RetrievalScalarsAndIds() {
        Map<String, Object> mp =
                Map.of(
                        "retrievalDenseCandidateCount",
                        1,
                        "retrievalAfterFilterCount",
                        1,
                        "contextChunkCount",
                        1,
                        "promptContextCharCount",
                        241,
                        "sourceCount",
                        1,
                        "retrieved_chunk_ids",
                        List.of("snap:doc:0"),
                        "retrieved_document_ids",
                        List.of("doc-1"));

        Map<String, Object> row = new LinkedHashMap<>();
        RagPresetRetrievalExportSupport.putJsonExportFields(row, mp);

        assertThat(row.get("retrievalDenseCandidateCount")).isEqualTo(1);
        assertThat(row.get("contextChunkCount")).isEqualTo(1);
        assertThat(row.get("promptContextCharCount")).isEqualTo(241);
        assertThat(row.get("sourceCount")).isEqualTo(1);
        assertThat(row.get("retrievedChunkIds")).isEqualTo(List.of("snap:doc:0"));
        assertThat(row.get("retrievedDocumentIds")).isEqualTo(List.of("doc-1"));
        assertThat(row.get("effectiveContextPresent")).isEqualTo(true);
    }

    @Test
    void putJsonExportFields_promotesAdvancedRetrievalScalars() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("advancedRetrievalApplied", true);
        mp.put("advancedRetrievalStrategy", "HYBRID_DENSE_SPARSE_RRF_RERANK");
        mp.put("denseCandidateCount", 5);
        mp.put("sparseCandidateCount", 2);
        mp.put("hybridCandidateCount", 7);
        mp.put("mergedCandidateCount", 6);
        mp.put("rerankApplied", true);
        mp.put("hybridApplied", true);
        mp.put("candidateOrigins", "dense=5;sparse=2;fused=6");

        Map<String, Object> row = new LinkedHashMap<>();
        RagPresetRetrievalExportSupport.putJsonExportFields(row, mp);

        assertThat(row.get("advancedRetrievalApplied")).isEqualTo(true);
        assertThat(row.get("denseCandidateCount")).isEqualTo(5);
        assertThat(row.get("sparseCandidateCount")).isEqualTo(2);
        assertThat(row.get("hybridApplied")).isEqualTo(true);
        assertThat(row.get("candidateOrigins")).isEqualTo("dense=5;sparse=2;fused=6");
    }

    @Test
    void putJsonExportFields_promotesAdvisorTelemetryScalars() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_ENABLED, true);
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_ROUTE, true);
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_ATTEMPTED, true);
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_APPLIED, true);
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_NAME, "retrievalAdvisor");
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_TYPE, "RETRIEVAL");
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_CONTRIBUTION_TYPE, "retrieval_guidance");
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_RESULT_USED, true);

        Map<String, Object> row = new LinkedHashMap<>();
        RagPresetRetrievalExportSupport.putJsonExportFields(row, mp);

        assertThat(row.get(RagPresetAdvisorMetrics.KEY_ADVISOR_ROUTE)).isEqualTo(true);
        assertThat(row.get(RagPresetAdvisorMetrics.KEY_ADVISOR_NAME)).isEqualTo("retrievalAdvisor");
        assertThat(row.get(RagPresetAdvisorMetrics.KEY_ADVISOR_APPLIED)).isEqualTo(true);
    }

    @Test
    void putCsvExportFields_promotesAdvancedRetrievalRouteAndModeFields() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("retrievalRoute", "HYBRID_DENSE_SPARSE_METADATA");
        mp.put("retrievalMode", "HYBRID_DENSE_SPARSE");
        mp.put("rerankNoopReason", "order_unchanged");
        mp.put("originalContextCharCount", 500);
        mp.put("materializationStrategy", "HYBRID");

        Map<String, String> row = new LinkedHashMap<>();
        RagPresetRetrievalExportSupport.putCsvExportFields(row, mp);

        assertThat(row.get("retrievalRoute")).isEqualTo("HYBRID_DENSE_SPARSE_METADATA");
        assertThat(row.get("retrievalMode")).isEqualTo("HYBRID_DENSE_SPARSE");
        assertThat(row.get("rerankNoopReason")).isEqualTo("order_unchanged");
        assertThat(row.get("originalContextCharCount")).isEqualTo("500");
        assertThat(row.get("materializationStrategy")).isEqualTo("HYBRID");
        assertThat(RagPresetRetrievalExportSupport.FLAT_CSV_KEYS).contains("retrievalRoute", "retrievalMode");
    }

    @Test
    void analysisJsonKeys_includeCalibratedMatchFields() {
        assertThat(RagPresetRetrievalExportSupport.ANALYSIS_JSON_KEYS)
                .contains(
                        ExpectedAnswerMatchResult.KEY_CONTAINED_RAW,
                        ExpectedAnswerMatchResult.KEY_MATCHED,
                        ExpectedAnswerMatchResult.KEY_MATCH_TYPE,
                        ExpectedAnswerMatchResult.KEY_MATCH_CONFIDENCE,
                        ExpectedAnswerMatchResult.KEY_MATCH_REASON,
                        ExpectedAnswerMatchResult.KEY_MATCH_VERSION);
    }

    @Test
    void putJsonExportFields_promotesCalibratedMatchFields() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(ExpectedAnswerMatchResult.KEY_CONTAINED_RAW, false);
        mp.put(ExpectedAnswerMatchResult.KEY_MATCHED, true);
        mp.put(ExpectedAnswerMatchResult.KEY_MATCH_TYPE, "NUMERIC_VALUE_MATCH");
        mp.put(ExpectedAnswerMatchResult.KEY_MATCH_CONFIDENCE, "HIGH");
        mp.put(ExpectedAnswerMatchResult.KEY_MATCH_REASON, "numeric_value_equal");
        mp.put(ExpectedAnswerMatchResult.KEY_MATCH_VERSION, "1");

        Map<String, Object> row = new LinkedHashMap<>();
        RagPresetRetrievalExportSupport.putJsonExportFields(row, mp);

        assertThat(row.get(ExpectedAnswerMatchResult.KEY_MATCHED)).isEqualTo(true);
        assertThat(row.get(ExpectedAnswerMatchResult.KEY_MATCH_TYPE)).isEqualTo("NUMERIC_VALUE_MATCH");
    }

    @Test
    void putCsvExportFields_promotesRetrievalScalars() {
        Map<String, Object> mp =
                Map.of(
                        "retrievalDenseCandidateCount",
                        2,
                        "contextChunkCount",
                        2,
                        "promptContextCharCount",
                        100,
                        "sourceCount",
                        2,
                        "retrieved_chunk_ids",
                        List.of("a", "b"));

        Map<String, String> row = new LinkedHashMap<>();
        RagPresetRetrievalExportSupport.putCsvExportFields(row, mp);

        assertThat(row.get("retrievalDenseCandidateCount")).isEqualTo("2");
        assertThat(row.get("contextChunkCount")).isEqualTo("2");
        assertThat(row.get("retrievedChunkIds")).isEqualTo("a;b");
        assertThat(row.get("effectiveContextPresent")).isEqualTo("true");
    }

    @Test
    void putJsonExportFields_promotesBaselineScalars() {
        Map<String, Object> mp =
                new LinkedHashMap<>(
                        Map.of(
                                "workflowName",
                                "DirectLlmWorkflow",
                                "useRetrieval",
                                false,
                                "corpusRequired",
                                false,
                                "requiresVectorIndex",
                                false,
                                "groupKey",
                                "DIRECT_LLM",
                                "corpusChars",
                                0,
                                "naiveFullCorpusInPromptEnabled",
                                false));

        Map<String, Object> row = new LinkedHashMap<>();
        RagPresetRetrievalExportSupport.putJsonExportFields(row, mp);

        assertThat(row.get("workflowName")).isEqualTo("DirectLlmWorkflow");
        assertThat(row.get("useRetrieval")).isEqualTo(false);
        assertThat(row.get("corpusRequired")).isEqualTo(false);
        assertThat(row.get("requiresVectorIndex")).isEqualTo(false);
        assertThat(row.get("groupKey")).isEqualTo("DIRECT_LLM");
        assertThat(row.get("corpusChars")).isEqualTo(0);
        assertThat(row.get("naiveFullCorpusInPromptEnabled")).isEqualTo(false);
        assertThat(mp.get("finalAnswerSource")).isEqualTo("DIRECT_LLM");
    }

    @Test
    void putCsvExportFields_infersDirectLlmFinalAnswerSource() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("groupKey", "DIRECT_LLM");

        Map<String, String> row = new LinkedHashMap<>();
        RagPresetRetrievalExportSupport.putCsvExportFields(row, mp);

        assertThat(mp.get("finalAnswerSource")).isEqualTo("DIRECT_LLM");
    }

    @Test
    void putJsonExportFields_promotesFunctionProposalFields() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_MODE, "BACKEND_DETERMINISTIC");
        mp.put(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_SOURCE, "QUERY_SHAPE");
        mp.put(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_VALID, true);

        Map<String, Object> row = new LinkedHashMap<>();
        RagPresetRetrievalExportSupport.putJsonExportFields(row, mp);

        assertThat(row.get(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_MODE)).isEqualTo("BACKEND_DETERMINISTIC");
        assertThat(row.get(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_SOURCE)).isEqualTo("QUERY_SHAPE");
        assertThat(row.get(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_VALID)).isEqualTo(true);
    }

    @Test
    void putJsonExportFields_promotesAnalysisScalarsWhenPresent() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("answerability", "ANSWERABLE");
        mp.put("finalScore", 0.85);
        mp.put("retrievalCoverageStatus", "HAS_CONTEXT");
        mp.put("retrievalQualityStatus", "NOT_AVAILABLE");
        mp.put("queryTypeMatch", "MATCH");

        Map<String, Object> row = new LinkedHashMap<>();
        RagPresetRetrievalExportSupport.putJsonExportFields(row, mp);

        assertThat(row.get("answerability")).isEqualTo("ANSWERABLE");
        assertThat(row.get("finalScore")).isEqualTo(0.85);
        assertThat(row.get("retrievalCoverageStatus")).isEqualTo("HAS_CONTEXT");
        assertThat(row.get("queryTypeMatch")).isEqualTo("MATCH");
    }

    @Test
    void putJsonExportFields_promotesSnapshotSupportsMetadataAndFinalScoreAvailability() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("activeSnapshotCapabilities", Map.of("supportsMetadata", true, "materializationStrategy", "CHUNK_LEVEL"));
        mp.put(RagPresetAnalysisMetrics.KEY_SCORE_UNAVAILABLE_REASON, "");

        Map<String, Object> row = new LinkedHashMap<>();
        RagPresetRetrievalExportSupport.putJsonExportFields(row, mp);

        assertThat(row.get("supportsMetadata")).isEqualTo(true);
        assertThat(row.get("finalScoreAvailable")).isEqualTo(true);
        assertThat(row.get("finalScoreStatus")).isEqualTo(ScoreExportSupport.STATUS_AVAILABLE);
    }

    @Test
    void putCsvExportFields_promotesSnapshotSupportsMetadataAndFinalScoreAvailability() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("activeSnapshotCapabilities", Map.of("supportsMetadata", true));
        mp.put(RagPresetAnalysisMetrics.KEY_SCORE_UNAVAILABLE_REASON, "no_scoring_signal");

        Map<String, String> row = new LinkedHashMap<>();
        RagPresetRetrievalExportSupport.putCsvExportFields(row, mp);

        assertThat(row.get("supportsMetadata")).isEqualTo("true");
        assertThat(row.get("finalScoreAvailable")).isEqualTo("false");
        assertThat(row.get("finalScoreStatus")).isEqualTo(ScoreExportSupport.STATUS_UNAVAILABLE);
    }

    @Test
    void buildHumanItemRow_includesFlatBaselineMetadataForP0() {
        UUID campaignId = UUID.randomUUID();
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(UUID.randomUUID());
        run.setLlmModelId("gemma3:4b");
        run.setBenchmarkKind("RAG_PRESET_END_TO_END");

        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setId(UUID.randomUUID());
        item.setBenchmarkKind("RAG_PRESET_END_TO_END");
        item.setQuestionText("q");
        item.setExpectedAnswer("a");
        item.setActualAnswer("g");
        item.setEvaluatedAt(Instant.now());
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("presetCode", "P0");
        mp.put("item_outcome", "EXECUTED");
        mp.put("workflowName", "DirectLlmWorkflow");
        mp.put("useRetrieval", false);
        mp.put("corpusRequired", false);
        mp.put("requiresVectorIndex", false);
        mp.put("groupKey", "DIRECT_LLM");
        mp.put("corpusChars", 0);
        mp.put("naiveFullCorpusInPromptEnabled", false);
        item.setMetricsPayload(mp);

        Map<String, Object> row =
                LabCampaignHumanExportBuilder.buildHumanItemRow(
                        campaignId, "RAG_PRESET_BENCHMARK", "PRESET_CODE", run, item);

        assertThat(row.get("workflowName")).isEqualTo("DirectLlmWorkflow");
        assertThat(row.get("corpusRequired")).isEqualTo(false);
        assertThat(row.get("requiresVectorIndex")).isEqualTo(false);
        assertThat(row.get("groupKey")).isEqualTo("DIRECT_LLM");
    }

    @Test
    void buildHumanItemRow_includesFlatRetrievalMetadataForP2() {
        UUID campaignId = UUID.randomUUID();
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(UUID.randomUUID());
        run.setLlmModelId("gemma3:4b");
        run.setBenchmarkKind("RAG_PRESET_END_TO_END");

        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setId(UUID.randomUUID());
        item.setBenchmarkKind("RAG_PRESET_END_TO_END");
        item.setQuestionText("q");
        item.setExpectedAnswer("a");
        item.setActualAnswer("g");
        item.setEvaluatedAt(Instant.now());
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("presetCode", "P2");
        mp.put("item_outcome", "EXECUTED");
        mp.put("workflowName", "DocumentDenseRagWorkflow");
        mp.put("retrievalRoute", "DOCUMENT_DENSE");
        mp.put("materializationStrategy", "DOCUMENT_LEVEL");
        mp.put("retrievalDenseCandidateCount", 2);
        mp.put("contextChunkCount", 1);
        item.setMetricsPayload(mp);

        Map<String, Object> row =
                LabCampaignHumanExportBuilder.buildHumanItemRow(
                        campaignId, "RAG_PRESET_BENCHMARK", "PRESET_CODE", run, item);

        assertThat(row.get("workflowName")).isEqualTo("DocumentDenseRagWorkflow");
        assertThat(row.get("retrievalRoute")).isEqualTo("DOCUMENT_DENSE");
        assertThat(row.get("materializationStrategy")).isEqualTo("DOCUMENT_LEVEL");
        assertThat(row.get("retrievalDenseCandidateCount")).isEqualTo(2);
    }

    @Test
    void buildHumanItemRow_includesFlatRetrievalMetadataForP3() {
        UUID campaignId = UUID.randomUUID();
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(UUID.randomUUID());
        run.setLlmModelId("gemma3:4b");
        run.setBenchmarkKind("RAG_PRESET_END_TO_END");

        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setId(UUID.randomUUID());
        item.setBenchmarkKind("RAG_PRESET_END_TO_END");
        item.setQuestionText("q");
        item.setExpectedAnswer("a");
        item.setActualAnswer("g");
        item.setEvaluatedAt(Instant.now());
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("presetCode", "P3");
        mp.put("item_outcome", "EXECUTED");
        mp.put("workflowName", "ChunkDenseRagWorkflow");
        mp.put("retrievalDenseCandidateCount", 1);
        mp.put("retrievalAfterFilterCount", 1);
        mp.put("contextChunkCount", 1);
        mp.put("promptContextCharCount", 241);
        mp.put("sourceCount", 1);
        mp.put("retrieved_chunk_ids", List.of("snap:hash:0"));
        mp.put(
                "sources",
                List.of(
                        Map.of(
                                "filename",
                                "acta.pdf",
                                "documentId",
                                "doc-1",
                                "chunkId",
                                "snap:hash:0",
                                "snippet",
                                "text")));
        item.setMetricsPayload(mp);

        Map<String, Object> row =
                LabCampaignHumanExportBuilder.buildHumanItemRow(
                        campaignId, "RAG_PRESET_BENCHMARK", "PRESET_CODE", run, item);

        assertThat(row.get("retrievalDenseCandidateCount")).isEqualTo(1);
        assertThat(row.get("retrievedChunkIds")).isEqualTo(List.of("snap:hash:0"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> sources = (List<Map<String, String>>) row.get("sources");
        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().get("chunkId")).isEqualTo("snap:hash:0");
    }
}
