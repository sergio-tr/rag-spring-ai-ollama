package com.uniovi.rag.application.service.runtime.judge;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.judge.JudgeCandidateSource;
import com.uniovi.rag.domain.runtime.judge.JudgeMode;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JudgePolicyResolverTest {

    private final JudgePolicyResolver resolver = new JudgePolicyResolver();

    @Test
    void resolve_disabledByConfig_marksNotEligible_andRetryForbidden() {
        ExecutionContext ctx = ctx(rag(false));

        var out =
                resolver.resolve(
                        ctx,
                        TestPlans.plan(),
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        "DirectLlmWorkflow",
                        JudgeCandidateSource.WORKFLOW);

        assertThat(out.mode()).isEqualTo(JudgeMode.DISABLED);
        assertThat(out.eligible()).isFalse();
        assertThat(out.retryAllowed()).isFalse();
    }

    @Test
    void resolve_enabled_allowsRetry_onlyForWorkflowCandidateSource() {
        ExecutionContext ctx = ctx(rag(true), RuntimeOperationKind.LAB_PROCESS);

        var workflow =
                resolver.resolve(
                        ctx,
                        TestPlans.plan(),
                        AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE,
                        "ChunkDenseRagWorkflow",
                        JudgeCandidateSource.WORKFLOW);
        assertThat(workflow.eligible()).isTrue();
        assertThat(workflow.retryAllowed()).isTrue();

        var tool =
                resolver.resolve(
                        ctx,
                        TestPlans.plan(),
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                        "deterministic-tool",
                        JudgeCandidateSource.DETERMINISTIC_TOOL);
        assertThat(tool.eligible()).isTrue();
        assertThat(tool.retryAllowed()).isFalse();

        var fc =
                resolver.resolve(
                        ctx,
                        TestPlans.plan(),
                        AdaptiveRouteKind.FUNCTION_CALLING_ROUTE,
                        "function-calling",
                        JudgeCandidateSource.FUNCTION_CALLING);
        assertThat(fc.eligible()).isTrue();
        assertThat(fc.retryAllowed()).isFalse();
    }

    private static RagConfig rag(boolean judgeEnabled) {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
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
        return ctx(rag, RuntimeOperationKind.CHAT_MESSAGE);
    }

    private static ExecutionContext ctx(RagConfig rag, RuntimeOperationKind operationKind) {
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
                operationKind,
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
}

