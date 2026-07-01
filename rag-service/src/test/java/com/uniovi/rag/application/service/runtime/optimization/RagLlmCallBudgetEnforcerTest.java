package com.uniovi.rag.application.service.runtime.optimization;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagLlmCallBudgetEnforcerTest {

    @AfterEach
    void tearDown() {
        RagLlmCallBudgetEnforcer.clear();
    }

    @Test
    void skipsOptionalSecondaryWhenBudgetExceeded() {
        ExecutionContext ctx = interactiveCtx(false, false, false);
        RagLlmCallBudgetEnforcer.bind(ctx);
        assertThat(RagLlmCallBudgetEnforcer.tryAllowSecondary("query-rewrite")).isTrue();
        RagLlmCallBudgetEnforcer.recordCompleted("query-rewrite");
        assertThat(RagLlmCallBudgetEnforcer.tryAllowSecondary("conversation-condense")).isFalse();
        assertThat(RagLlmCallBudgetEnforcer.tryAllowPrimary("primary-answer")).isTrue();
    }

    @Test
    void providerSkipsWhenBudgetDenied() {
        ExecutionContext ctx = interactiveCtx(false, false, false);
        RagLlmCallBudgetEnforcer.bind(ctx);
        RagLlmCallBudgetEnforcer.recordCompleted("query-rewrite");
        assertThatThrownBy(() -> {
                    if (!RagLlmCallBudgetEnforcer.tryAllowSecondary("llm-ranker")) {
                        throw new OptionalLlmCallBudgetSkippedException("llm-ranker");
                    }
                })
                .isInstanceOf(OptionalLlmCallBudgetSkippedException.class);
    }

    @Test
    void memoryPresetAllowsMoreSecondaries() {
        ExecutionContext ctx = interactiveCtx(true, true, false);
        RagLlmCallBudgetEnforcer.bind(ctx);
        RagLlmCallBudgetPolicy.PresetBudget budget = RagLlmCallBudgetPolicy.budgetFor(ctx);
        assertThat(budget.maxSecondaryCalls()).isGreaterThanOrEqualTo(3);
    }

    private static ExecutionContext interactiveCtx(boolean memory, boolean judge, boolean reasoning) {
        RagConfig rag = rag(memory, judge, reasoning);
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
                "dime qué actas tienen 20 asistentes",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "",
                null,
                Optional.empty(),
                Optional.empty(),
                "trace-1",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "",
                "",
                Optional.empty(),
                memory ? ConversationMemoryOutcome.NO_HISTORY_AVAILABLE : ConversationMemoryOutcome.DISABLED_BY_CONFIG,
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

    private static RagConfig rag(boolean memory, boolean judge, boolean reasoning) {
        return new RagConfig(
                false,
                false,
                false,
                false,
                reasoning,
                false,
                false,
                false,
                true,
                true,
                false,
                memory,
                false,
                judge,
                5,
                0.2,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.HYBRID);
    }
}
