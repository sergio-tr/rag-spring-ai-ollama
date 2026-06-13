package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FunctionCallingTelemetryMapperTest {

    @Test
    void mapsSuccessfulFunctionCall() {
        ExecutionTrace trace = traceWithFc(
                AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name(),
                true,
                FunctionCallingOutcome.EXECUTED_SUCCESS.name(),
                "COUNT_DOCUMENTS_TOOL",
                true,
                false);

        Map<String, Object> tel = FunctionCallingTelemetryMapper.fromTrace(trace);

        assertThat(tel.get("functionCallAttempted")).isEqualTo(true);
        assertThat(tel.get("functionCallingUsed")).isEqualTo(true);
        assertThat(tel.get("functionCallSucceeded")).isEqualTo(true);
        assertThat(tel.get("functionCallArgumentsValid")).isEqualTo(true);
        assertThat(tel.get("functionCallName")).isEqualTo("countDocuments");
        assertThat(tel.get("functionResultUsedAsFinal")).isEqualTo(true);
        assertThat(tel.get("functionResultUsedAsContext")).isEqualTo(true);
        assertThat(tel.get("functionCallRoute")).isEqualTo(AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name());
    }

    @Test
    void mapsModelDeclinedFallback() {
        ExecutionTrace trace = traceWithFc(
                AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name(),
                true,
                FunctionCallingOutcome.MODEL_DECLINED.name(),
                "",
                false,
                true);

        Map<String, Object> tel = FunctionCallingTelemetryMapper.fromTrace(trace);

        assertThat(tel.get("functionCallingUsed")).isEqualTo(false);
        assertThat(tel.get("functionCallFallbackReason")).isEqualTo("model_declined");
        assertThat(tel.get("functionCallName")).isEqualTo("");
    }

    @Test
    void mapsInvalidArgumentsFallback() {
        ExecutionTrace trace = traceWithFc(
                AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name(),
                true,
                FunctionCallingOutcome.INVALID_MODEL_OUTPUT.name(),
                "",
                false,
                true);

        Map<String, Object> tel = FunctionCallingTelemetryMapper.fromTrace(trace);

        assertThat(tel.get("functionCallArgumentsValid")).isEqualTo(false);
        assertThat(tel.get("functionCallFallbackReason")).isEqualTo("invalid_model_output");
    }

    @Test
    void mapsNotApplicableWithoutAttempt() {
        ExecutionTrace trace = traceWithFc(
                AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE.name(),
                false,
                FunctionCallingOutcome.NOT_APPLICABLE.name(),
                "",
                false,
                false);

        Map<String, Object> tel = FunctionCallingTelemetryMapper.fromTrace(trace);

        assertThat(tel.get("functionCallAttempted")).isEqualTo(false);
        assertThat(tel.get("functionCallFallbackReason")).isEqualTo("function_not_applicable");
        assertThat(tel.get("functionCallRoute")).isEqualTo("");
    }

    private static ExecutionTrace traceWithFc(
            String routeKind,
            boolean attempted,
            String outcome,
            String toolKind,
            boolean shortCircuited,
            boolean routingFallback) {
        ExecutionTrace base = ExecutionTrace.placeholder();
        return new ExecutionTrace(
                base.stages(),
                "function-calling",
                base.retrievalUsed(),
                base.metadataUsed(),
                base.usedKnowledgeSnapshotIds(),
                base.usedResolvedConfigSnapshotId(),
                base.usedConfigHash(),
                base.queryPlanVersion(),
                base.classifierStatus(),
                base.classifierLabel(),
                base.expectedAnswerShape(),
                base.ambiguityStatus(),
                base.compatibilitySeverity(),
                base.memoryAttempted(),
                base.memoryOutcome(),
                base.memoryHistoryLoaded(),
                base.memoryCondensationAttempted(),
                base.memoryCondensationUsed(),
                base.memoryFallbackApplied(),
                true,
                base.routingOutcome(),
                routeKind,
                routingFallback,
                base.routingFallbackRouteKind(),
                base.routingWorkflowSelectorInvoked(),
                base.deterministicToolOutcome(),
                base.deterministicToolKind(),
                base.deterministicToolDetail(),
                attempted,
                outcome,
                toolKind,
                shortCircuited,
                base.retrievalDiagnostics(),
                base.advisorAttempted(),
                base.advisorShortCircuitedContextPrep(),
                base.advisorKindsExecuted(),
                base.advisorOutcome(),
                base.packedContextBlockCount(),
                base.packedContextSourceCount(),
                base.judgeAttempted(),
                base.judgeCandidateSource(),
                base.judgeRetryRequested(),
                base.judgeRetryAttempted(),
                base.judgeRetrySucceeded(),
                base.judgeFinalOutcome(),
                base.judgeFinalAnswerFromRetry(),
                base.judgeKind(),
                base.judgeDetail(),
                base.clarificationAttempted(),
                base.clarificationOutcome(),
                base.clarificationPendingStateConsumed(),
                base.clarificationQuestionAsked(),
                base.originalQuery(),
                base.retrievalQuery(),
                base.packedContextPreview(),
                base.sourceCount(),
                base.retrievedDocumentNames(),
                base.answerGroundingPolicy(),
                base.promptContextCharCount(),
                base.abstentionTriggered(),
                base.abstentionReason());
    }
}
