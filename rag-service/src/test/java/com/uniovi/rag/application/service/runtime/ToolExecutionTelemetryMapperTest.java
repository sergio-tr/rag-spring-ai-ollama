package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolExecutionTelemetryMapperTest {

    @Test
    void mapsSuccessfulToolExecution() {
        ExecutionTrace trace =
                withToolFields(
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name(),
                        DeterministicToolOutcome.EXECUTED_SUCCESS.name(),
                        "COUNT_DOCUMENTS_TOOL",
                        "executed=COUNT_DOCUMENTS_TOOL",
                        false);
        Map<String, Object> tel = ToolExecutionTelemetryMapper.fromTrace(trace);
        assertThat(tel.get("deterministicToolRoute")).isEqualTo(true);
        assertThat(tel.get("functionCallingUsed")).isEqualTo(false);
        assertThat(tel.get("toolApplicable")).isEqualTo(true);
        assertThat(tel.get("toolExecuted")).isEqualTo(true);
        assertThat(tel.get("toolSucceeded")).isEqualTo(true);
        assertThat(tel.get("toolResultUsedAsFinal")).isEqualTo(true);
        assertThat(tel.get("toolName")).isEqualTo("countDocuments");
    }

    @Test
    void mapsNotApplicableFallback() {
        ExecutionTrace trace =
                withToolFields(
                        AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE.name(),
                        DeterministicToolOutcome.NOT_APPLICABLE.name(),
                        "",
                        "tool_not_applicable",
                        false);
        Map<String, Object> tel = ToolExecutionTelemetryMapper.fromTrace(trace);
        assertThat(tel.get("deterministicToolRoute")).isEqualTo(false);
        assertThat(tel.get("toolApplicable")).isEqualTo(false);
        assertThat(tel.get("toolFallbackReason")).isEqualTo("tool_not_applicable");
    }

    @Test
    void mapsRouteSuppressionTelemetryFromToolDetail() {
        ExecutionTrace trace =
                withToolFields(
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name(),
                        DeterministicToolOutcome.NOT_APPLICABLE.name(),
                        "",
                        "route_suppressed_by_classifier;routeSuppressedByClassifier=true;routeSuppressedReason=classifier_low_confidence;heuristicRouteUsed=false",
                        false);
        Map<String, Object> tel = ToolExecutionTelemetryMapper.fromTrace(trace);
        assertThat(tel.get("routeSuppressedByClassifier")).isEqualTo(true);
        assertThat(tel.get("routeSuppressedReason")).isEqualTo("classifier_low_confidence");
        assertThat(tel.get("heuristicRouteUsed")).isEqualTo(false);
    }

    @Test
    void mapsSuccessfulFunctionCallingWithoutDeterministicRoute() {
        ExecutionTrace trace =
                withToolFields(
                        AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name(),
                        DeterministicToolOutcome.NOT_ATTEMPTED.name(),
                        "",
                        "suppressed_by_routing_fc",
                        false);
        trace =
                new ExecutionTrace(
                        trace.stages(),
                        "function-calling",
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
                        true,
                        trace.routingOutcome(),
                        AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name(),
                        false,
                        trace.routingFallbackRouteKind(),
                        trace.routingWorkflowSelectorInvoked(),
                        DeterministicToolOutcome.NOT_ATTEMPTED.name(),
                        "",
                        "suppressed_by_routing_fc",
                        true,
                        FunctionCallingOutcome.EXECUTED_SUCCESS.name(),
                        "COUNT_DOCUMENTS_TOOL",
                        true,
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

        Map<String, Object> tel = ToolExecutionTelemetryMapper.fromTrace(trace);
        assertThat(tel.get("deterministicToolRoute")).isEqualTo(false);
        assertThat(tel.get("functionCallingUsed")).isEqualTo(true);
        assertThat(tel.get("functionCallName")).isEqualTo("countDocuments");
        assertThat(tel.get("routingRouteKind")).isEqualTo(AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name());
        assertThat(tel.get("executionRoute")).isEqualTo(AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name());
        assertThat(tel.get("toolExecuted")).isEqualTo(false);
    }

    @Test
    void mapsSuccessfulAdvisorRouteWithoutFunctionCallingOrDeterministicTool() {
        ExecutionTrace trace =
                withToolFields(
                        AdaptiveRouteKind.ADVISOR_ROUTE.name(),
                        com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome.NOT_ATTEMPTED.name(),
                        "",
                        "suppressed_by_routing_advisor",
                        false);
        trace =
                new ExecutionTrace(
                        trace.stages(),
                        "advisor",
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
                        true,
                        trace.routingOutcome(),
                        AdaptiveRouteKind.ADVISOR_ROUTE.name(),
                        false,
                        trace.routingFallbackRouteKind(),
                        trace.routingWorkflowSelectorInvoked(),
                        trace.deterministicToolOutcome(),
                        trace.deterministicToolKind(),
                        trace.deterministicToolDetail(),
                        false,
                        trace.functionCallingOutcome(),
                        trace.functionCallingToolKind(),
                        false,
                        trace.retrievalDiagnostics(),
                        true,
                        true,
                        "RETRIEVAL_ADVISOR,CONTEXT_PACKING_ADVISOR",
                        com.uniovi.rag.domain.runtime.advisor.AdvisorOutcome.EXECUTED_SUCCESS.name(),
                        2,
                        1,
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

        Map<String, Object> tel = ToolExecutionTelemetryMapper.fromTrace(trace);
        assertThat(tel.get("advisorRoute")).isEqualTo(true);
        assertThat(tel.get("advisorAttempted")).isEqualTo(true);
        assertThat(tel.get("advisorApplied")).isEqualTo(true);
        assertThat(tel.get("deterministicToolRoute")).isEqualTo(false);
        assertThat(tel.get("functionCallingUsed")).isEqualTo(false);
        assertThat(tel.get("executionRoute")).isEqualTo(AdaptiveRouteKind.ADVISOR_ROUTE.name());
        assertThat(tel.get("advisorName")).isEqualTo("retrievalAdvisor,contextPackingAdvisor");
    }

    @Test
    void mapsWorkflowFallbackAfterDeterministicPrimaryRoute() {
        ExecutionTrace trace =
                withToolFields(
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name(),
                        DeterministicToolOutcome.NOT_APPLICABLE.name(),
                        "",
                        "tool_not_applicable",
                        true);
        Map<String, Object> tel = ToolExecutionTelemetryMapper.fromTrace(trace);
        assertThat(tel.get("deterministicToolRoute")).isEqualTo(false);
        assertThat(tel.get("toolFallbackReason")).isEqualTo("tool_not_applicable");
    }

    private static ExecutionTrace withToolFields(
            String routeKind, String toolOutcome, String toolKind, String toolDetail, boolean routingFallbackApplied) {
        ExecutionTrace base = ExecutionTrace.placeholder();
        return new ExecutionTrace(
                base.stages(),
                "deterministic-tool",
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
                routingFallbackApplied,
                base.routingFallbackRouteKind(),
                base.routingWorkflowSelectorInvoked(),
                toolOutcome,
                toolKind,
                toolDetail,
                base.functionCallingAttempted(),
                base.functionCallingOutcome(),
                base.functionCallingToolKind(),
                base.functionCallingShortCircuited(),
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
