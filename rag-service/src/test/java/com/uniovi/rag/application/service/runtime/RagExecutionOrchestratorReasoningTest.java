package com.uniovi.rag.application.service.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationPolicyResolver;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationStrategy;
import com.uniovi.rag.application.service.runtime.advisor.AdvisorPolicyResolver;
import com.uniovi.rag.application.service.runtime.advisor.AdvisorStrategy;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingPolicyResolver;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingStrategy;
import com.uniovi.rag.application.service.runtime.judge.JudgeStrategy;
import com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline;
import com.uniovi.rag.application.service.runtime.reasoning.AnswerVerificationService;
import com.uniovi.rag.application.service.runtime.reasoning.StructuredAnswerPlanService;
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
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingExecutionResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.routing.RouteExecutionGate;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.domain.runtime.reasoning.StructuredAnswerPlan;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class RagExecutionOrchestratorReasoningTest {

    @Test
    void execute_whenReasoningEnabled_injectsAnswerPlanIntoPrompt_andAddsTraceStages() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("""
                        {
                          "strategy":"SAFE_STRUCTURED_PLAN",
                          "objective":"Summarize the acta if (and only if) exact evidence exists.",
                          "expectedEvidence":["Exact date match"],
                          "answerConstraints":["Use only context for document claims","If mismatch, say so"],
                          "verificationChecklist":["Check exact date","No invented facts"],
                          "safeSummary":"Verify exact evidence; avoid guessing."
                        }
                        """);
        when(chatClient.prompt().user(anyString()).options(any()).call().content())
                .thenReturn("""
                        {
                          "strategy":"SAFE_STRUCTURED_PLAN",
                          "objective":"Summarize the acta if (and only if) exact evidence exists.",
                          "expectedEvidence":["Exact date match"],
                          "answerConstraints":["Use only context for document claims","If mismatch, say so"],
                          "verificationChecklist":["Check exact date","No invented facts"],
                          "safeSummary":"Verify exact evidence; avoid guessing."
                        }
                        """);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("final-answer");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content()).thenReturn("final-answer");

        RagConfig rag =
                new RagConfig(
                        false,
                        false,
                        false,
                        false,
                        true,
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
                        0.2,
                        "llm",
                        "emb",
                        "clf",
                        "PLAN_AND_VERIFY",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
        ExecutionContext base = ctxWithoutPlan(rag);
        QueryPlan plan = plan();
        ExecutionContext withPlan = ctxWithPlan(base, plan);

        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        when(qu.buildPlan(any())).thenReturn(plan);

        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        when(factory.attachQueryPlan(base, plan)).thenReturn(withPlan);
        when(factory.attachStructuredAnswerPlan(any(), any()))
                .thenAnswer(inv -> {
                    ExecutionContext ctx = inv.getArgument(0);
                    StructuredAnswerPlan p = (StructuredAnswerPlan) inv.getArgument(1);
                    return new ExecutionContext(
                            ctx.userId(),
                            ctx.projectId(),
                            ctx.conversationId(),
                            ctx.userQuery(),
                            ctx.operationKind(),
                            ctx.resolved(),
                            ctx.effectiveSystemPrompt(),
                            ctx.knowledgeSnapshotSelection(),
                            ctx.configHash(),
                            ctx.pinnedResolvedConfigSnapshotId(),
                            ctx.correlationId(),
                            ctx.documentFilter(),
                            ctx.chatModelOverride(),
                            ctx.queryPlan(),
                            ctx.advisorPackedContextSet(),
                            Optional.of(p),
                            ctx.preMemoryPlanningInputText(),
                            ctx.effectivePlanningInputText(),
                            ctx.memorySlice(),
                            ctx.memoryOutcome(),
                            ctx.memoryStageTraces(),
                            ctx.memoryAttempted(),
                            ctx.memoryHistoryLoaded(),
                            ctx.memoryCondensationAttempted(),
                            ctx.memoryCondensationUsed(),
                            ctx.memoryFallbackApplied(),
                            ctx.pendingClarificationLoadedForTrace(),
                            ctx.validPendingExistedAtLoad(),
                            ctx.invalidPendingRecoveredThisTurn(),
                            ctx.clarificationDisableReason(),
                            ctx.originatingUserMessageId(),
                            ctx.routingAttempted(),
                            ctx.routingOutcome(),
                            ctx.routingRouteKind(),
                            ctx.routingFallbackApplied(),
                            ctx.routingFallbackRouteKind(),
                            ctx.routingWorkflowSelectorInvoked(),
                            ctx.routingStageTraces());
                });

        WorkflowSelector selector = mock(WorkflowSelector.class);
        DirectLlmWorkflow direct = new DirectLlmWorkflow(chatClient, null);
        when(selector.select(any())).thenReturn(direct);

        ClarificationPolicyResolver clarificationPolicyResolver = mock(ClarificationPolicyResolver.class);
        when(clarificationPolicyResolver.resolve(any(), any()))
                .thenReturn(new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, "test"));
        ClarificationStrategy clarificationStrategy = mock(ClarificationStrategy.class);

        AdaptiveRoutingStrategy routing = mock(AdaptiveRoutingStrategy.class);
        when(routing.execute(any(), any()))
                .thenReturn(new AdaptiveRoutingExecutionResult(
                        AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                        false,
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        false,
                        Optional.empty(),
                        false,
                        new RouteExecutionGate(
                                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                                true,
                                false,
                                false,
                                false,
                                false,
                                Optional.empty(),
                                false),
                        List.of()));

        RagExecutionOrchestrator orch =
                new RagExecutionOrchestrator(
                        selector,
                        direct,
                        qu,
                        factory,
                        mock(DeterministicToolStrategy.class),
                        mock(FunctionCallingPolicyResolver.class),
                        mock(FunctionCallingStrategy.class),
                        mock(AdvisorPolicyResolver.class),
                        mock(AdvisorStrategy.class),
                        clarificationPolicyResolver,
                        clarificationStrategy,
                        routing,
                        mock(JudgeStrategy.class),
                        new StructuredAnswerPlanService(chatClient, new ObjectMapper()),
                        new AnswerVerificationService(chatClient),
                        mock(ObjectProvider.class));

        var out = orch.execute(base);

        assertThat(out.answerText()).isEqualTo("final-answer");
        assertThat(out.executionTrace().stages()).anyMatch(s -> "reasoning_plan".equals(s.stageName()));
        assertThat(out.executionTrace().stages()).anyMatch(s -> "reasoning_verify".equals(s.stageName()));

        verify(chatClient.prompt().system(anyString()))
                .user(argThat((String m) -> m != null && m.contains("<AnswerPlan>")));
    }

    private static QueryPlan plan() {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "normalized",
                "question?",
                "label",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("normalized", "disabled"),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                "corr",
                "clf",
                List.of());
    }

    private static ExecutionContext ctxWithoutPlan(RagConfig rag) {
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user question",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "user question",
                "user question",
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
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }

    private static ExecutionContext ctxWithPlan(ExecutionContext base, QueryPlan plan) {
        return new ExecutionContext(
                base.userId(),
                base.projectId(),
                base.conversationId(),
                base.userQuery(),
                base.operationKind(),
                base.resolved(),
                base.effectiveSystemPrompt(),
                base.knowledgeSnapshotSelection(),
                base.configHash(),
                base.pinnedResolvedConfigSnapshotId(),
                base.correlationId(),
                base.documentFilter(),
                base.chatModelOverride(),
                Optional.of(plan),
                base.advisorPackedContextSet(),
                Optional.empty(),
                base.preMemoryPlanningInputText(),
                base.effectivePlanningInputText(),
                base.memorySlice(),
                base.memoryOutcome(),
                base.memoryStageTraces(),
                base.memoryAttempted(),
                base.memoryHistoryLoaded(),
                base.memoryCondensationAttempted(),
                base.memoryCondensationUsed(),
                base.memoryFallbackApplied(),
                base.pendingClarificationLoadedForTrace(),
                base.validPendingExistedAtLoad(),
                base.invalidPendingRecoveredThisTurn(),
                base.clarificationDisableReason(),
                base.originatingUserMessageId(),
                base.routingAttempted(),
                base.routingOutcome(),
                base.routingRouteKind(),
                base.routingFallbackApplied(),
                base.routingFallbackRouteKind(),
                base.routingWorkflowSelectorInvoked(),
                base.routingStageTraces());
    }
}

