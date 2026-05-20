package com.uniovi.rag.interfaces.rest.dto.tracereplay;

import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayRequest;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeTraceReplayResponseDtoTest {

    @Test
    void fromReplayHttp_truncates_answer_failure_and_route_fields() {
        String longAnswer = "a".repeat(RuntimeTraceReplayResponseDto.MAX_ANSWER_TEXT_CHARS + 50);
        String longFailure = "b".repeat(RuntimeTraceReplayResponseDto.MAX_FAILURE_DETAIL_CHARS + 30);
        UUID traceId = UUID.randomUUID();
        var result =
                new RuntimeTraceReplayResult(
                        RuntimeTraceReplayOutcome.REPLAY_FAILED_SAFE,
                        Optional.of(longAnswer),
                        Optional.of(longFailure),
                        Optional.empty());

        RuntimeTraceReplayResponseDto dto =
                RuntimeTraceReplayResponseDto.fromReplayHttp(
                        result,
                        RuntimeTraceReplayRequest.byTraceId(UUID.randomUUID(), traceId),
                        traceId,
                        null,
                        null);

        assertThat(dto.answerText().length()).isEqualTo(RuntimeTraceReplayResponseDto.MAX_ANSWER_TEXT_CHARS);
        assertThat(dto.failureDetail().length()).isEqualTo(RuntimeTraceReplayResponseDto.MAX_FAILURE_DETAIL_CHARS);
        assertThat(dto.routingRouteKind().length()).isEqualTo(0);
        assertThat(dto.workflowName().length()).isEqualTo(0);
    }

    @Test
    void fromReplayHttp_caps_routing_and_workflow_from_transient_trace() {
        String pad = "z".repeat(RuntimeTraceReplayResponseDto.MAX_ROUTE_OR_WORKFLOW_CHARS + 5);
        ExecutionTrace trace = withRouteAndWorkflow(ExecutionTrace.placeholder(), pad, pad);
        UUID tid = UUID.randomUUID();
        var result = RuntimeTraceReplayResult.success("x", trace);
        RuntimeTraceReplayResponseDto dto =
                RuntimeTraceReplayResponseDto.fromReplayHttp(
                        result, RuntimeTraceReplayRequest.byTraceId(UUID.randomUUID(), tid), tid, null, null);

        assertThat(dto.routingRouteKind().length()).isEqualTo(RuntimeTraceReplayResponseDto.MAX_ROUTE_OR_WORKFLOW_CHARS);
        assertThat(dto.workflowName().length()).isEqualTo(RuntimeTraceReplayResponseDto.MAX_ROUTE_OR_WORKFLOW_CHARS);
        assertThat(dto.transientTraceSummary().stageCount()).isEqualTo(0);
    }

    private static ExecutionTrace withRouteAndWorkflow(
            ExecutionTrace base, String workflowName, String routingRouteKind) {
        return new ExecutionTrace(
                base.stages(),
                workflowName,
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
                base.routingAttempted(),
                base.routingOutcome(),
                routingRouteKind,
                base.routingFallbackApplied(),
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
