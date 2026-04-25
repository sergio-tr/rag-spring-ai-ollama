package com.uniovi.rag.application.service.runtime.tracereplay;

import com.uniovi.rag.application.service.runtime.ExecutionWorkflow;
import com.uniovi.rag.application.service.runtime.memory.ConversationMemoryStrategy;
import com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolExecutor;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryExecutionResult;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fast branch coverage for {@link RuntimeTraceReplayStrategy} without full replay wiring (wave 6.05).
 */
@ExtendWith(MockitoExtension.class)
class RuntimeTraceReplayStrategyBranchTest {

    @Mock private QueryUnderstandingPipeline queryUnderstandingPipeline;

    @Mock private ConversationMemoryStrategy conversationMemoryStrategy;

    @Mock private DeterministicToolExecutor deterministicToolExecutor;

    @Test
    void execute_returnsUnsupported_forFunctionCallingRoute() {
        RuntimeTraceReplayStrategy strategy =
                new RuntimeTraceReplayStrategy(
                        queryUnderstandingPipeline, conversationMemoryStrategy, deterministicToolExecutor, List.of());
        PinnedReplayExecutionSpec pin =
                new PinnedReplayExecutionSpec(AdaptiveRouteKind.FUNCTION_CALLING_ROUTE, "", List.of(), "");
        RuntimeTraceReplayResult r =
                strategy.execute(
                        mock(RuntimeExecutionTraceDetailDto.class),
                        mock(RuntimeTraceReplayInputLoader.ReplayLoadedInputs.class),
                        pin);
        assertThat(r.outcome()).isEqualTo(RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY);
    }

    @Test
    void execute_returnsFailedSafe_forUnknownWorkflow() {
        RuntimeTraceReplayStrategy strategy =
                new RuntimeTraceReplayStrategy(
                        queryUnderstandingPipeline, conversationMemoryStrategy, deterministicToolExecutor, List.of());
        PinnedReplayExecutionSpec pin =
                new PinnedReplayExecutionSpec(
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE, "MissingWorkflow", List.of(), "");
        RuntimeTraceReplayResult r =
                strategy.execute(
                        mock(RuntimeExecutionTraceDetailDto.class),
                        mock(RuntimeTraceReplayInputLoader.ReplayLoadedInputs.class),
                        pin);
        assertThat(r.outcome()).isEqualTo(RuntimeTraceReplayOutcome.REPLAY_FAILED_SAFE);
        assertThat(r.failureDetail()).hasValueSatisfying(d -> assertThat(d).contains("unknown_workflow"));
    }

    @Test
    void execute_returnsFailedSafe_whenKnowledgeSnapshotsRequiredButEmpty() {
        ExecutionWorkflow wf = mock(ExecutionWorkflow.class);
        when(wf.workflowName()).thenReturn("DocumentDenseRagWorkflow");
        RuntimeTraceReplayStrategy strategy =
                new RuntimeTraceReplayStrategy(
                        queryUnderstandingPipeline,
                        conversationMemoryStrategy,
                        deterministicToolExecutor,
                        List.of(wf));
        PinnedReplayExecutionSpec pin =
                new PinnedReplayExecutionSpec(
                        AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE, "DocumentDenseRagWorkflow", List.of(), "");
        RuntimeTraceReplayResult r =
                strategy.execute(
                        mock(RuntimeExecutionTraceDetailDto.class),
                        mock(RuntimeTraceReplayInputLoader.ReplayLoadedInputs.class),
                        pin);
        assertThat(r.failureDetail()).hasValueSatisfying(d -> assertThat(d).contains("knowledge_snapshots"));
    }

    @Test
    void execute_deterministicToolRoute_success() {
        UUID traceId = UUID.randomUUID();
        RuntimeExecutionTraceDetailDto trace = detailDto(traceId, "   ");
        RuntimeTraceReplayInputLoader.ReplayLoadedInputs inputs = replayInputs("count docs");
        when(queryUnderstandingPipeline.buildPlan(any())).thenReturn(minimalQueryPlan());
        when(conversationMemoryStrategy.executeWithEligibleHistory(any(), anyString(), anyList()))
                .thenReturn(
                        new ConversationMemoryExecutionResult(
                                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                                Optional.empty(),
                                false,
                                false,
                                false,
                                "count docs",
                                List.of()));
        when(deterministicToolExecutor.execute(any(), any(), any()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                "42",
                                Map.of(),
                                List.of()));

        RuntimeTraceReplayStrategy strategy =
                new RuntimeTraceReplayStrategy(
                        queryUnderstandingPipeline, conversationMemoryStrategy, deterministicToolExecutor, List.of());
        PinnedReplayExecutionSpec pin =
                new PinnedReplayExecutionSpec(
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE, "", List.of(), "COUNT_DOCUMENTS_TOOL");

        RuntimeTraceReplayResult r = strategy.execute(trace, inputs, pin);
        assertThat(r.outcome()).isEqualTo(RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED);
        assertThat(r.answerText()).hasValue("42");
    }

    @Test
    void execute_directWorkflowRoute_success() {
        UUID traceId = UUID.randomUUID();
        RuntimeExecutionTraceDetailDto trace = detailDto(traceId, "");
        RuntimeTraceReplayInputLoader.ReplayLoadedInputs inputs = replayInputs("hello");
        when(queryUnderstandingPipeline.buildPlan(any())).thenReturn(minimalQueryPlan());
        when(conversationMemoryStrategy.executeWithEligibleHistory(any(), anyString(), anyList()))
                .thenReturn(
                        new ConversationMemoryExecutionResult(
                                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                                Optional.empty(),
                                false,
                                false,
                                false,
                                "hello",
                                List.of()));

        ExecutionWorkflow wf = mock(ExecutionWorkflow.class);
        when(wf.workflowName()).thenReturn("DirectLlmWorkflow");
        when(wf.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "ok", "DirectLlmWorkflow", false, false, List.of(), "", List.of()));

        RuntimeTraceReplayStrategy strategy =
                new RuntimeTraceReplayStrategy(
                        queryUnderstandingPipeline, conversationMemoryStrategy, deterministicToolExecutor, List.of(wf));
        PinnedReplayExecutionSpec pin =
                new PinnedReplayExecutionSpec(AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE, "DirectLlmWorkflow", List.of(), "");

        RuntimeTraceReplayResult r = strategy.execute(trace, inputs, pin);
        assertThat(r.outcome()).isEqualTo(RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED);
        assertThat(r.answerText()).hasValue("ok");
        assertThat(r.transientReplayTrace()).isNotEmpty();
    }

    @Test
    void execute_deterministicToolRoute_failedSafe_forUnknownToolKind() {
        RuntimeTraceReplayStrategy strategy =
                new RuntimeTraceReplayStrategy(
                        queryUnderstandingPipeline, conversationMemoryStrategy, deterministicToolExecutor, List.of());
        PinnedReplayExecutionSpec pin =
                new PinnedReplayExecutionSpec(
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE, "", List.of(), "NOT_A_REAL_TOOL");

        RuntimeTraceReplayResult r =
                strategy.execute(
                        detailDto(UUID.randomUUID(), "c"),
                        mock(RuntimeTraceReplayInputLoader.ReplayLoadedInputs.class),
                        pin);
        assertThat(r.outcome()).isEqualTo(RuntimeTraceReplayOutcome.REPLAY_FAILED_SAFE);
        assertThat(r.failureDetail()).hasValueSatisfying(d -> assertThat(d).contains("unknown_tool_kind"));
    }

    @Test
    void execute_deterministicToolRoute_failedSafe_whenToolDoesNotSucceed() {
        when(conversationMemoryStrategy.executeWithEligibleHistory(any(), anyString(), anyList()))
                .thenReturn(
                        new ConversationMemoryExecutionResult(
                                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                                Optional.empty(),
                                false,
                                false,
                                false,
                                "q",
                                List.of()));
        when(queryUnderstandingPipeline.buildPlan(any())).thenReturn(minimalQueryPlan());
        when(deterministicToolExecutor.execute(any(), any(), any()))
                .thenReturn(
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_ATTEMPTED, List.of("nope"), Optional.empty()));

        RuntimeTraceReplayStrategy strategy =
                new RuntimeTraceReplayStrategy(
                        queryUnderstandingPipeline, conversationMemoryStrategy, deterministicToolExecutor, List.of());
        PinnedReplayExecutionSpec pin =
                new PinnedReplayExecutionSpec(
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE, "", List.of(), "COUNT_DOCUMENTS_TOOL");

        RuntimeTraceReplayResult r =
                strategy.execute(detailDto(UUID.randomUUID(), "c"), replayInputs("q"), pin);
        assertThat(r.outcome()).isEqualTo(RuntimeTraceReplayOutcome.REPLAY_FAILED_SAFE);
        assertThat(r.failureDetail()).hasValueSatisfying(d -> assertThat(d).contains("tool_outcome"));
    }

    private static RuntimeExecutionTraceDetailDto detailDto(UUID id, String correlationId) {
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        UUID u = UUID.randomUUID();
        return new RuntimeExecutionTraceDetailDto(
                id,
                now,
                u,
                u,
                u,
                u,
                correlationId,
                u,
                "h",
                "w",
                false,
                "",
                false,
                "",
                "",
                false,
                false,
                "",
                "",
                "",
                false,
                "",
                "",
                false,
                "",
                1,
                Map.of(),
                List.of());
    }

    private static RuntimeTraceReplayInputLoader.ReplayLoadedInputs replayInputs(String userContent) {
        ResolvedRuntimeConfig resolved = mock(ResolvedRuntimeConfig.class);
        RagConfig rag =
                new RagConfig(
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        5,
                        0.5,
                        "llm",
                        "emb",
                        "cls",
                        "rs",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
        when(resolved.effectiveSystemPrompt()).thenReturn("sys");
        when(resolved.toRagConfig()).thenReturn(rag);

        MessageEntity msg = mock(MessageEntity.class);
        when(msg.getContent()).thenReturn(userContent);
        return new RuntimeTraceReplayInputLoader.ReplayLoadedInputs(
                resolved, msg, mock(ConversationEntity.class), List.of(), List.of("all"));
    }

    private static QueryPlan minimalQueryPlan() {
        return new QueryPlan(
                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                "raw",
                "effective",
                "normalized",
                "rewritten",
                "label",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote("test"),
                StructuredRewriteResult.identityDisabled("rewritten", "test"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "corr",
                "cls",
                List.of());
    }
}
