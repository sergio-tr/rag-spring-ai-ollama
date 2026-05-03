package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.advisor.AdvisorPolicyResolver;
import com.uniovi.rag.application.service.runtime.advisor.AdvisorStrategy;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationPolicyResolver;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationStrategy;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingPolicyResolver;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingStrategy;
import com.uniovi.rag.application.service.runtime.judge.JudgeStrategy;
import com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline;
import com.uniovi.rag.application.service.runtime.routing.AdaptiveRoutingStrategy;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolStrategy;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationExecutionResult;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestion;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.judge.JudgeCandidateSource;
import com.uniovi.rag.domain.runtime.judge.JudgeExecutionResult;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.*;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingExecutionResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.routing.RouteExecutionGate;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagExecutionOrchestratorJudgeIntegrationTest {

    @Test
    void judgeDisabled_doesNotInvokeJudgeStrategy_andTraceShowsNotAttempted() {
        QueryPlan plan = plan();
        ExecutionContext in = ctx(rag(false));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        AdvisorPolicyResolver advisorPolicy = mock(AdvisorPolicyResolver.class);
        AdvisorStrategy advisorStrategy = mock(AdvisorStrategy.class);
        ClarificationPolicyResolver clarificationPolicyResolver = mock(ClarificationPolicyResolver.class);
        ClarificationStrategy clarificationStrategy = mock(ClarificationStrategy.class);
        AdaptiveRoutingStrategy routingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);

        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenReturn(in);
        when(clarificationPolicyResolver.resolve(any(), any()))
                .thenReturn(new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, ""));
        when(routingStrategy.execute(any(), eq(plan)))
                .thenReturn(
                        new AdaptiveRoutingExecutionResult(
                                AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED,
                                true,
                                AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                                false,
                                Optional.empty(),
                                false,
                                new RouteExecutionGate(
                                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                                        false,
                                        true,
                                        false,
                                        false,
                                        false,
                                        Optional.empty(),
                                        false),
                                List.of()));

        when(tools.tryExecute(any(), eq(plan)))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                "tool-answer",
                                Map.of(),
                                List.of()));

        RagExecutionOrchestrator orchestrator =
                new RagExecutionOrchestrator(
                        workflowSelector,
                        mock(DirectLlmWorkflow.class),
                        qu,
                        factory,
                        tools,
                        fcPolicy,
                        fcStrategy,
                        advisorPolicy,
                        advisorStrategy,
                        clarificationPolicyResolver,
                        clarificationStrategy,
                        routingStrategy,
                        judgeStrategy);

        var out = orchestrator.execute(in);
        assertThat(out.answerText()).isEqualTo("tool-answer");
        assertThat(out.executionTrace().judgeAttempted()).isFalse();
        assertThat(out.executionTrace().judgeFinalOutcome()).isEqualTo(JudgeOutcome.NOT_ATTEMPTED.name());
        verifyNoInteractions(judgeStrategy);
    }

    @Test
    void judgeEnabled_retrySucceeded_replacesAnswerText_andTraceFlags() {
        QueryPlan plan = plan();
        ExecutionContext in = ctx(rag(true));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        AdvisorPolicyResolver advisorPolicy = mock(AdvisorPolicyResolver.class);
        AdvisorStrategy advisorStrategy = mock(AdvisorStrategy.class);
        ClarificationPolicyResolver clarificationPolicyResolver = mock(ClarificationPolicyResolver.class);
        ClarificationStrategy clarificationStrategy = mock(ClarificationStrategy.class);
        AdaptiveRoutingStrategy routingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);

        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenReturn(in);
        when(clarificationPolicyResolver.resolve(any(), any()))
                .thenReturn(new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, ""));
        when(routingStrategy.execute(any(), eq(plan)))
                .thenReturn(
                        new AdaptiveRoutingExecutionResult(
                                AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED,
                                true,
                                AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                                false,
                                Optional.empty(),
                                false,
                                new RouteExecutionGate(
                                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                                        false,
                                        true,
                                        false,
                                        false,
                                        false,
                                        Optional.empty(),
                                        false),
                                List.of()));
        when(tools.tryExecute(any(), eq(plan)))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                "tool-answer",
                                Map.of(),
                                List.of()));

        when(judgeStrategy.execute(any(), eq(plan), any(), anyString(), eq(JudgeCandidateSource.DETERMINISTIC_TOOL), eq("tool-answer")))
                .thenReturn(
                        new JudgeExecutionResult(
                                true,
                                JudgeOutcome.RETRY_SUCCEEDED,
                                true,
                                true,
                                true,
                                "judged-answer",
                                true,
                                List.of(new ExecutionStageTrace("judge_evaluate", 0L, ExecutionStageOutcome.SUCCESS, "ok"))));

        RagExecutionOrchestrator orchestrator =
                new RagExecutionOrchestrator(
                        workflowSelector,
                        mock(DirectLlmWorkflow.class),
                        qu,
                        factory,
                        tools,
                        fcPolicy,
                        fcStrategy,
                        advisorPolicy,
                        advisorStrategy,
                        clarificationPolicyResolver,
                        clarificationStrategy,
                        routingStrategy,
                        judgeStrategy);

        var out = orchestrator.execute(in);
        assertThat(out.answerText()).isEqualTo("judged-answer");
        ExecutionTrace t = out.executionTrace();
        assertThat(t.judgeAttempted()).isTrue();
        assertThat(t.judgeFinalOutcome()).isEqualTo(JudgeOutcome.RETRY_SUCCEEDED.name());
        assertThat(t.judgeFinalAnswerFromRetry()).isTrue();
        assertThat(t.stages().stream().anyMatch(s -> "judge_evaluate".equals(s.stageName()))).isTrue();
        verify(judgeStrategy, times(1)).execute(any(), any(), any(), anyString(), any(), anyString());
    }

    @Test
    void clarificationAsk_shortCircuit_doesNotInvokeJudgeStrategy() {
        QueryPlan plan = plan();
        ExecutionContext in = ctx(rag(true));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        AdvisorPolicyResolver advisorPolicy = mock(AdvisorPolicyResolver.class);
        AdvisorStrategy advisorStrategy = mock(AdvisorStrategy.class);
        ClarificationPolicyResolver clarificationPolicyResolver = mock(ClarificationPolicyResolver.class);
        ClarificationStrategy clarificationStrategy = mock(ClarificationStrategy.class);
        AdaptiveRoutingStrategy routingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);

        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenReturn(in);
        when(clarificationPolicyResolver.resolve(any(), any()))
                .thenReturn(
                        new ClarificationDecision(
                                true,
                                ClarificationOutcome.ASKED_CLARIFICATION,
                                new ClarificationQuestion(
                                        "q?",
                                        ClarificationQuestionKind.MISSING_DATE,
                                        List.of("date")),
                                ""));
        when(clarificationStrategy.executeAsk(any(), any(), any()))
                .thenReturn(new ClarificationExecutionResult(
                        ClarificationOutcome.ASKED_CLARIFICATION,
                        "q?",
                        List.of()));

        RagExecutionOrchestrator orchestrator =
                new RagExecutionOrchestrator(
                        workflowSelector,
                        mock(DirectLlmWorkflow.class),
                        qu,
                        factory,
                        tools,
                        fcPolicy,
                        fcStrategy,
                        advisorPolicy,
                        advisorStrategy,
                        clarificationPolicyResolver,
                        clarificationStrategy,
                        routingStrategy,
                        judgeStrategy);

        var out = orchestrator.execute(in);
        assertThat(out.workflowName()).isEqualTo("clarification");
        verifyNoInteractions(judgeStrategy);
        verifyNoInteractions(routingStrategy);
    }

    private static RagConfig rag(boolean judgeEnabled) {
        return new RagConfig(
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                true,
                judgeEnabled,
                5,
                0.2,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    private static ExecutionContext ctx(RagConfig rag) {
        UUID uid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        SystemPromptLayers.empty(),
                        "",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        return new ExecutionContext(
                uid,
                pid,
                cid,
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "",
                "",
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty());
    }

    private static QueryPlan plan() {
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

