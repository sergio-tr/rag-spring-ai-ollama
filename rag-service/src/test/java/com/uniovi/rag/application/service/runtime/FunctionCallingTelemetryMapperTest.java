package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;

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

    @Test
    void mapsProposalStageFields() {
        ExecutionTrace trace = traceWithFc(
                AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name(),
                true,
                FunctionCallingOutcome.EXECUTED_SUCCESS.name(),
                "COUNT_DOCUMENTS_TOOL",
                true,
                false);
        ExecutionTrace withProposal =
                new ExecutionTrace(
                        List.of(
                                new ExecutionStageTrace(
                                        "function_calling_proposal",
                                        0L,
                                        ExecutionStageOutcome.SUCCESS,
                                        "functionProposalMode=BACKEND_DETERMINISTIC;functionProposalSource=QUERY_SHAPE;functionProposalValid=true;functionProposalRepairAttempted=false;functionProposalRepairSucceeded=false;backendFunctionCallAttempted=true;nativeProviderFunctionCallAttempted=false;functionCallName=countDocuments;functionToolKind=COUNT_DOCUMENTS_TOOL")),
                        trace.workflowName(),
                        trace.retrievalUsed(),
                        trace.metadataUsed(),
                        trace.usedKnowledgeSnapshotIds(),
                        trace.usedResolvedConfigSnapshotId(),
                        trace.usedConfigHash(),
                        trace.queryPlanVersion(),
                        trace.classifierStatus(),
                        trace.classifierLabel(),
                        trace.expectedAnswerShape(),
                        trace.ambiguityStatus(),
                        trace.compatibilitySeverity(),
                        trace.memoryAttempted(),
                        trace.memoryOutcome(),
                        trace.memoryHistoryLoaded(),
                        trace.memoryCondensationAttempted(),
                        trace.memoryCondensationUsed(),
                        trace.memoryFallbackApplied(),
                        trace.routingAttempted(),
                        trace.routingOutcome(),
                        trace.routingRouteKind(),
                        trace.routingFallbackApplied(),
                        trace.routingFallbackRouteKind(),
                        trace.routingWorkflowSelectorInvoked(),
                        trace.deterministicToolOutcome(),
                        trace.deterministicToolKind(),
                        trace.deterministicToolDetail(),
                        trace.functionCallingAttempted(),
                        trace.functionCallingOutcome(),
                        trace.functionCallingToolKind(),
                        trace.functionCallingShortCircuited(),
                        trace.retrievalDiagnostics(),
                        trace.advisorAttempted(),
                        trace.advisorShortCircuitedContextPrep(),
                        trace.advisorKindsExecuted(),
                        trace.advisorOutcome(),
                        trace.packedContextBlockCount(),
                        trace.packedContextSourceCount(),
                        trace.judgeAttempted(),
                        trace.judgeCandidateSource(),
                        trace.judgeRetryRequested(),
                        trace.judgeRetryAttempted(),
                        trace.judgeRetrySucceeded(),
                        trace.judgeFinalOutcome(),
                        trace.judgeFinalAnswerFromRetry(),
                        trace.judgeKind(),
                        trace.judgeDetail(),
                        trace.clarificationAttempted(),
                        trace.clarificationOutcome(),
                        trace.clarificationPendingStateConsumed(),
                        trace.clarificationQuestionAsked(),
                        trace.originalQuery(),
                        trace.retrievalQuery(),
                        trace.packedContextPreview(),
                        trace.sourceCount(),
                        trace.retrievedDocumentNames(),
                        trace.answerGroundingPolicy(),
                        trace.promptContextCharCount(),
                        trace.abstentionTriggered(),
                        trace.abstentionReason());

        Map<String, Object> tel = FunctionCallingTelemetryMapper.fromTrace(withProposal);

        assertThat(tel.get("functionProposalMode")).isEqualTo("BACKEND_DETERMINISTIC");
        assertThat(tel.get("functionProposalSource")).isEqualTo("QUERY_SHAPE");
        assertThat(tel.get("functionProposalValid")).isEqualTo(true);
        assertThat(tel.get("backendFunctionCallAttempted")).isEqualTo(true);
        assertThat(tel.get("nativeProviderFunctionCallAttempted")).isEqualTo(false);
        assertThat(tel.get("functionToolKind")).isEqualTo("COUNT_DOCUMENTS_TOOL");
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
