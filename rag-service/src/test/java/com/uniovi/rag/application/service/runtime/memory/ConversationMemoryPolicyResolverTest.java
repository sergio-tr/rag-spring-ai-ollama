package com.uniovi.rag.application.service.runtime.memory;

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
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryDecision;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryMode;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryPolicyResolverTest {

    private final ConversationMemoryPolicyResolver resolver = new ConversationMemoryPolicyResolver();

    @Test
    void resolve_disabledByConfig_whenMemoryEnabledFalse() {
        ExecutionContext ctx = ctx(rag(false), UUID.randomUUID());
        ConversationMemoryDecision d = resolver.resolve(ctx);
        assertThat(d.mode()).isEqualTo(ConversationMemoryMode.DISABLED);
        assertThat(d.attemptMemory()).isFalse();
        assertThat(d.attemptCondensation()).isFalse();
        assertThat(d.reasons()).contains("memoryEnabled=false");
    }

    @Test
    void resolve_noConversationScope_whenConversationIdNull() {
        ExecutionContext ctx = ctx(rag(true), null);
        ConversationMemoryDecision d = resolver.resolve(ctx);
        assertThat(d.mode()).isEqualTo(ConversationMemoryMode.DISABLED);
        assertThat(d.attemptMemory()).isFalse();
        assertThat(d.reasons()).contains("no_conversation_scope");
    }

    @Test
    void resolve_enabled_attemptsMemoryAndCondensation_whenConfigOnAndConversationScoped() {
        ExecutionContext ctx = ctx(rag(true), UUID.randomUUID());
        ConversationMemoryDecision d = resolver.resolve(ctx);
        assertThat(d.mode()).isEqualTo(ConversationMemoryMode.ENABLED_CONDENSE_FOR_PLANNING);
        assertThat(d.attemptMemory()).isTrue();
        assertThat(d.attemptCondensation()).isTrue();
        assertThat(d.maxHistoryTurns()).isEqualTo(ConversationMemoryDecision.FIXED_MAX_HISTORY_TURNS_P12);
    }

    private static ExecutionContext ctx(RagConfig rag, UUID conversationId) {
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
        String q = "q";
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                conversationId,
                q,
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
                q,
                q,
                Optional.empty(),
                conversationId == null ? ConversationMemoryOutcome.NO_CONVERSATION_SCOPE : ConversationMemoryOutcome.DISABLED_BY_CONFIG,
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

    private static RagConfig rag(boolean memoryEnabled) {
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
                memoryEnabled,
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
                MaterializationStrategy.CHUNK_LEVEL);
    }
}

