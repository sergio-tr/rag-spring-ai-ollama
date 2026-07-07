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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagLlmCallBudgetPolicyTest {

    @Test
    void budgetFor_minimalPresetWithExpansionAndNer_allowsBothSecondaryCalls() {
        RagConfig rag = rag(false, false, false, true, true);
        ExecutionContext ctx = ctx(rag);

        RagLlmCallBudgetPolicy.PresetBudget budget = RagLlmCallBudgetPolicy.budgetFor(ctx);

        assertThat(budget.maxSecondaryCalls()).isGreaterThanOrEqualTo(2);
        assertThat(budget.maxTotalCalls()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void budgetFor_minimalPresetWithoutQuLlmStages_keepsTightCap() {
        RagConfig rag = rag(false, false, false, false, false);
        ExecutionContext ctx = ctx(rag);

        RagLlmCallBudgetPolicy.PresetBudget budget = RagLlmCallBudgetPolicy.budgetFor(ctx);

        assertThat(budget.maxSecondaryCalls()).isEqualTo(1);
        assertThat(budget.maxTotalCalls()).isEqualTo(2);
    }

    private static ExecutionContext ctx(RagConfig rag) {
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
                "¿Qué fecha aparece en el ACTA 1?",
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

    private static RagConfig rag(
            boolean memory, boolean judge, boolean reasoning, boolean expansion, boolean ner) {
        return new RagConfig(
                expansion,
                ner,
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
                false,
                5,
                0.2,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                false,
                false,
                false,
                MaterializationStrategy.HYBRID);
    }
}
