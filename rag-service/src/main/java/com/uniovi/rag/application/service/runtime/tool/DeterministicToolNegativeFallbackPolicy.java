package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.List;
import java.util.Map;

/** When a successful tool answer is negative but dense retrieval has sources, prefer retrieval. */
public final class DeterministicToolNegativeFallbackPolicy {

    public static final String FINAL_ANSWER_SOURCE = "RETRIEVAL_FALLBACK";
    public static final String REJECTION_REASON = "tool_negative_answer_dense_retrieval_fallback";

    private DeterministicToolNegativeFallbackPolicy() {}

    public static boolean shouldDeferToolTerminalFinish(
            boolean retrievalEnabled, DeterministicToolExecutionResult toolResult) {
        if (!retrievalEnabled || !isSuccessfulTool(toolResult)) {
            return false;
        }
        return DeterministicToolNegativeAnswerDetector.isNegativeOrNoData(toolResult.answerText());
    }

    public static boolean shouldPreferRetrievalOverTool(
            DeterministicToolExecutionResult toolResult, RagExecutionResult retrievalResult, boolean retrievalAbstained) {
        if (!isSuccessfulTool(toolResult)
                || !DeterministicToolNegativeAnswerDetector.isNegativeOrNoData(toolResult.answerText())) {
            return false;
        }
        if (retrievalAbstained || retrievalResult == null) {
            return false;
        }
        return hasDenseRetrievalEvidence(retrievalResult);
    }

    public static boolean hasDenseRetrievalEvidence(RagExecutionResult retrievalResult) {
        if (retrievalResult == null) {
            return false;
        }
        if (retrievalResult.retrievalUsed() && hasNonBlankSources(retrievalResult.responseSources())) {
            return true;
        }
        return workflowStagesIndicateRetrievalSources(retrievalResult);
    }

    private static boolean workflowStagesIndicateRetrievalSources(RagExecutionResult retrievalResult) {
        if (retrievalResult.workflowStageTraces() == null) {
            return false;
        }
        return retrievalResult.workflowStageTraces().stream()
                .anyMatch(
                        stage ->
                                stage != null
                                        && stage.message() != null
                                        && stage.message().contains("sourceCount=")
                                        && !stage.message().contains("sourceCount=0"));
    }

    private static boolean hasNonBlankSources(List<Map<String, Object>> responseSources) {
        if (responseSources == null || responseSources.isEmpty()) {
            return false;
        }
        for (Map<String, Object> source : responseSources) {
            if (source == null || source.isEmpty()) {
                continue;
            }
            Object filename = source.get("filename");
            if (filename != null && !String.valueOf(filename).isBlank()) {
                return true;
            }
            Object snippet = source.get("snippet");
            if (snippet != null && !String.valueOf(snippet).isBlank()) {
                return true;
            }
            Object documentId = source.get("documentId");
            if (documentId != null && !String.valueOf(documentId).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSuccessfulTool(DeterministicToolExecutionResult toolResult) {
        return toolResult != null
                && toolResult.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS
                && toolResult.success();
    }
}
