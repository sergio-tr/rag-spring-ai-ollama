package com.uniovi.rag.application.service.runtime;

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
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatGenerationModelSelectorTest {

    @Test
    void prefersExplicitChatModelOverrideOverResolvedRagConfig() {
        ExecutionContext ctx = ctxWithModels(Optional.of("req-llm"), "resolved-llm");
        assertThat(ChatGenerationModelSelector.effectiveChatModelId(ctx)).contains("req-llm");
    }

    @Test
    void fallsBackToResolvedRagConfigWhenOverrideAbsent() {
        ExecutionContext ctx = ctxWithModels(Optional.empty(), "resolved-llm");
        assertThat(ChatGenerationModelSelector.effectiveChatModelId(ctx)).contains("resolved-llm");
    }

    @Test
    void emptyOverrideFallsBackToResolved() {
        ExecutionContext ctx = ctxWithModels(Optional.of("   "), "resolved-llm");
        assertThat(ChatGenerationModelSelector.effectiveChatModelId(ctx)).contains("resolved-llm");
    }

    private static ExecutionContext ctxWithModels(Optional<String> chatOverride, String resolvedLlm) {
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
                        true,
                        false,
                        false,
                        false,
                        false,
                        5,
                        0.2,
                        resolvedLlm,
                        "emb",
                        "cls",
                        "NONE",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
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
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "c",
                List.of("all"),
                chatOverride,
                Optional.empty(),
                Optional.empty(),
                "q",
                "q",
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
