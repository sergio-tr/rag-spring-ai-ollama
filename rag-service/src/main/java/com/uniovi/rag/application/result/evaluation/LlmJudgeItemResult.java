package com.uniovi.rag.application.result.evaluation;

import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One LLM-judge or RAG-preset benchmark row after query + judge evaluation.
 */
public record LlmJudgeItemResult(
        String question,
        String correctAnswer,
        String generatedAnswer,
        String llmEvaluation,
        String toolUsed,
        String queryType,
        boolean usedTool,
        String datasetQuestionId,
        BenchmarkItemOutcome itemOutcome,
        Long latencyMs,
        String errorCode,
        String reason,
        String errorMessage,
        String presetCode,
        String presetLabel,
        String difficulty,
        String evaluationProtocol,
        String llmModelId,
        String embeddingModelId,
        List<String> retrievedDocumentIds,
        List<String> relevantDocumentIds,
        Map<String, Object> chatTelemetry,
        Map<String, Object> baselineMetrics,
        Map<String, Object> labMetricsPayload) implements JudgeSummarizableRow {

    public LlmJudgeItemResult {
        retrievedDocumentIds = retrievedDocumentIds != null ? List.copyOf(retrievedDocumentIds) : List.of();
        relevantDocumentIds = relevantDocumentIds != null ? List.copyOf(relevantDocumentIds) : List.of();
        chatTelemetry = copyStringObjectMap(chatTelemetry);
        baselineMetrics = copyStringObjectMap(baselineMetrics);
        labMetricsPayload = copyStringObjectMap(labMetricsPayload);
    }

    private static Map<String, Object> copyStringObjectMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : source.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return Map.copyOf(out);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String question;
        private String correctAnswer = "";
        private String generatedAnswer = "";
        private String llmEvaluation = "";
        private String toolUsed;
        private String queryType;
        private boolean usedTool;
        private String datasetQuestionId;
        private BenchmarkItemOutcome itemOutcome = BenchmarkItemOutcome.EXECUTED;
        private Long latencyMs;
        private String errorCode;
        private String reason;
        private String errorMessage;
        private String presetCode;
        private String presetLabel;
        private String difficulty;
        private String evaluationProtocol;
        private String llmModelId;
        private String embeddingModelId;
        private List<String> retrievedDocumentIds = List.of();
        private List<String> relevantDocumentIds = List.of();
        private Map<String, Object> chatTelemetry = Map.of();
        private Map<String, Object> baselineMetrics = Map.of();
        private Map<String, Object> labMetricsPayload = Map.of();

        public Builder question(String question) {
            this.question = question;
            return this;
        }

        public Builder correctAnswer(String correctAnswer) {
            this.correctAnswer = correctAnswer;
            return this;
        }

        public Builder generatedAnswer(String generatedAnswer) {
            this.generatedAnswer = generatedAnswer;
            return this;
        }

        public Builder llmEvaluation(String llmEvaluation) {
            this.llmEvaluation = llmEvaluation;
            return this;
        }

        public Builder toolUsed(String toolUsed) {
            this.toolUsed = toolUsed;
            return this;
        }

        public Builder queryType(String queryType) {
            this.queryType = queryType;
            return this;
        }

        public Builder usedTool(boolean usedTool) {
            this.usedTool = usedTool;
            return this;
        }

        public Builder datasetQuestionId(String datasetQuestionId) {
            this.datasetQuestionId = datasetQuestionId;
            return this;
        }

        public Builder itemOutcome(BenchmarkItemOutcome itemOutcome) {
            this.itemOutcome = itemOutcome;
            return this;
        }

        public Builder latencyMs(Long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder presetCode(String presetCode) {
            this.presetCode = presetCode;
            return this;
        }

        public Builder presetLabel(String presetLabel) {
            this.presetLabel = presetLabel;
            return this;
        }

        public Builder difficulty(String difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public Builder evaluationProtocol(String evaluationProtocol) {
            this.evaluationProtocol = evaluationProtocol;
            return this;
        }

        public Builder llmModelId(String llmModelId) {
            this.llmModelId = llmModelId;
            return this;
        }

        public Builder embeddingModelId(String embeddingModelId) {
            this.embeddingModelId = embeddingModelId;
            return this;
        }

        public Builder retrievedDocumentIds(List<String> retrievedDocumentIds) {
            this.retrievedDocumentIds = retrievedDocumentIds;
            return this;
        }

        public Builder relevantDocumentIds(List<String> relevantDocumentIds) {
            this.relevantDocumentIds = relevantDocumentIds;
            return this;
        }

        public Builder chatTelemetry(Map<String, Object> chatTelemetry) {
            this.chatTelemetry = chatTelemetry;
            return this;
        }

        public Builder baselineMetrics(Map<String, Object> baselineMetrics) {
            this.baselineMetrics = baselineMetrics;
            return this;
        }

        public Builder labMetricsPayload(Map<String, Object> labMetricsPayload) {
            this.labMetricsPayload = labMetricsPayload;
            return this;
        }

        public LlmJudgeItemResult build() {
            return new LlmJudgeItemResult(
                    question,
                    correctAnswer,
                    generatedAnswer,
                    llmEvaluation,
                    toolUsed,
                    queryType,
                    usedTool,
                    datasetQuestionId,
                    itemOutcome,
                    latencyMs,
                    errorCode,
                    reason,
                    errorMessage,
                    presetCode,
                    presetLabel,
                    difficulty,
                    evaluationProtocol,
                    llmModelId,
                    embeddingModelId,
                    retrievedDocumentIds,
                    relevantDocumentIds,
                    chatTelemetry,
                    baselineMetrics,
                    labMetricsPayload);
        }
    }
}
