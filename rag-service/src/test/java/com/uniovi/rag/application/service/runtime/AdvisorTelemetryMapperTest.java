package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.advisor.AdvisorOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvisorTelemetryMapperTest {

    @Test
    void mapsSuccessfulAdvisorRoute() {
        ExecutionTrace trace =
                traceWithAdvisor(
                        AdaptiveRouteKind.ADVISOR_ROUTE.name(),
                        true,
                        AdvisorOutcome.EXECUTED_SUCCESS.name(),
                        "RETRIEVAL_ADVISOR,CONTEXT_PACKING_ADVISOR",
                        true,
                        2,
                        1,
                        false);

        Map<String, Object> tel = AdvisorTelemetryMapper.fromTrace(trace);

        assertThat(tel.get("advisorRoute")).isEqualTo(true);
        assertThat(tel.get("advisorAttempted")).isEqualTo(true);
        assertThat(tel.get("advisorApplied")).isEqualTo(true);
        assertThat(tel.get("advisorName")).isEqualTo("retrievalAdvisor,contextPackingAdvisor");
        assertThat(tel.get("advisorType")).isEqualTo("RETRIEVAL_AND_CONTEXT_PACKING");
        assertThat(tel.get("advisorChangedContext")).isEqualTo(true);
        assertThat(tel.get("advisorChangedPrompt")).isEqualTo(true);
        assertThat(tel.get("advisorResultUsed")).isEqualTo(true);
        assertThat(tel.get("executionRoute")).isEqualTo(AdaptiveRouteKind.ADVISOR_ROUTE.name());
    }

    @Test
    void mapsPolicySuppressedFallback() {
        ExecutionTrace trace =
                traceWithAdvisor(
                        AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE.name(),
                        false,
                        AdvisorOutcome.SUPPRESSED_BY_POLICY.name(),
                        "",
                        false,
                        0,
                        0,
                        false);

        Map<String, Object> tel = AdvisorTelemetryMapper.fromTrace(trace);

        assertThat(tel.get("advisorRoute")).isEqualTo(false);
        assertThat(tel.get("advisorAttempted")).isEqualTo(false);
        assertThat(tel.get("advisorApplied")).isEqualTo(false);
        assertThat(tel.get("advisorFallbackReason")).isEqualTo("policy_suppressed");
    }

    @Test
    void mapsRetrievalFailedFallback() {
        ExecutionTrace trace =
                traceWithAdvisor(
                        AdaptiveRouteKind.ADVISOR_ROUTE.name(),
                        true,
                        AdvisorOutcome.EXECUTED_FAILED_RETRIEVAL.name(),
                        "RETRIEVAL_ADVISOR",
                        false,
                        0,
                        0,
                        true);

        Map<String, Object> tel = AdvisorTelemetryMapper.fromTrace(trace);

        assertThat(tel.get("advisorAttempted")).isEqualTo(true);
        assertThat(tel.get("advisorApplied")).isEqualTo(false);
        assertThat(tel.get("advisorFallbackReason")).isEqualTo("retrieval_failed");
    }

    private static ExecutionTrace traceWithAdvisor(
            String routeKind,
            boolean attempted,
            String outcome,
            String kindsExecuted,
            boolean shortCircuited,
            int packedBlockCount,
            int packedSourceCount,
            boolean routingFallback) {
        ExecutionTrace base = ExecutionTrace.placeholder();
        return new ExecutionTrace(
                base.stages(),
                "advisor",
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
                base.functionCallingAttempted(),
                base.functionCallingOutcome(),
                base.functionCallingToolKind(),
                base.functionCallingShortCircuited(),
                base.retrievalDiagnostics(),
                attempted,
                shortCircuited,
                kindsExecuted,
                outcome,
                packedBlockCount,
                packedSourceCount,
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
