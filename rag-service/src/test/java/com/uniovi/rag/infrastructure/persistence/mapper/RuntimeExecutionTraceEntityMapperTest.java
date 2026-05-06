package com.uniovi.rag.infrastructure.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeExecutionTraceEntityMapperTest {

    @Test
    void toNewEntity_truncatesStageDetails_andCapsStageCount_andAddsTruncationMarker() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
        RuntimeExecutionTraceEntityMapper mapper = new RuntimeExecutionTraceEntityMapper(objectMapper);

        List<ExecutionStageTrace> stages = new ArrayList<>();
        String longDetail = "x".repeat(RuntimeExecutionTraceEntityMapper.MAX_STAGE_DETAIL_CHARS + 50);
        for (int i = 0; i < RuntimeExecutionTraceEntityMapper.MAX_STAGE_COUNT + 10; i++) {
            stages.add(new ExecutionStageTrace("s" + i, 0L, ExecutionStageOutcome.SUCCESS, longDetail));
        }

        ExecutionTrace trace = withStagesAndWorkflow(ExecutionTrace.placeholder(), stages, "DirectLlmWorkflow");

        var e =
                mapper.toNewEntity(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Optional.empty(),
                        Optional.empty(),
                        "corr",
                        trace);

        assertThat(e.getStagesJsonb()).isNotEmpty();
        assertThat(e.getStagesJsonb().size()).isEqualTo(RuntimeExecutionTraceEntityMapper.MAX_STAGE_COUNT + 1);
        assertThat(e.getStagesJsonb().get(0).get("message").toString().length())
                .isEqualTo(RuntimeExecutionTraceEntityMapper.MAX_STAGE_DETAIL_CHARS);
        assertThat(e.getStagesJsonb().get(e.getStagesJsonb().size() - 1)).containsEntry("truncated", true);
        assertThat(e.getExecutionTraceJsonb()).containsEntry("stages_truncated", true);
        assertThat(e.getExecutionTraceJsonb()).containsEntry("stages_count_original", stages.size());
    }

    private static ExecutionTrace withStagesAndWorkflow(
            ExecutionTrace base, List<ExecutionStageTrace> stages, String workflowName) {
        return new ExecutionTrace(
                stages,
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
                base.routingRouteKind(),
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
                base.retrievedDocumentNames());
    }
}

