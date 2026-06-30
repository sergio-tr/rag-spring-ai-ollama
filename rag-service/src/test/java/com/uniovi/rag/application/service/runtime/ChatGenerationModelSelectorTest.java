package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.uniovi.rag.application.exception.llm.LlmConfigurationException;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatGenerationModelSelectorTest {

    private ChatGenerationModelSelector selector;

    @BeforeEach
    void setUp() {
        selector = new ChatGenerationModelSelector(LlmModelCatalogTestSupport.catalogFrom(
                LlmModelCatalogTestSupport.openAiLiteLlmProperties()));
    }

    @AfterEach
    void tearDown() {
        OrchestrationLlmConfigScope.clear();
    }

    @Test
    void chatSelectorUsesResolvedOpenAiCompatibleModel() {
        prefersExplicitChatModelOverrideOverResolvedRagConfig();
    }

    @Test
    void chatSelectorIgnoresLegacyRagConfigOllamaModelWhenProviderIsOpenAiCompatible() {
        openAiProviderIgnoresLegacyRagConfigOllamaModel();
    }

    @Test
    void chatSelectorAllowsLegacyRagConfigOnlyForOllamaProvider() {
        ollamaProviderKeepsLegacyRagConfigModel();
    }

    @Test
    void chatSelectorRejectsModelNotConfiguredForProvider() {
        invalidOverrideForProviderFailsClearly();
    }

    @Test
    void chatSelectorRejectsEmbeddingModelAsChatModel() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());
        selector = new ChatGenerationModelSelector(catalog);
        bindOpenAiConfig("gpt-oss:20b");
        ExecutionContext ctx = ctxWithModels(Optional.of("qwen3-embedding:8b"), "qwen3-embedding:8b");
        assertThrows(LlmConfigurationException.class, () -> selector.effectiveChatModelId(ctx));
    }

    @Test
    void prefersExplicitChatModelOverrideOverResolvedRagConfig() {
        bindOpenAiConfig("gpt-oss:20b");
        ExecutionContext ctx = ctxWithModels(Optional.of("gpt-oss:20b"), "gemma3:4b");
        assertThat(selector.effectiveChatModelId(ctx)).contains("gpt-oss:20b");
    }

    @Test
    void openAiProviderIgnoresLegacyRagConfigOllamaModel() {
        bindOpenAiConfig("gpt-oss:20b");
        ExecutionContext ctx = ctxWithModels(Optional.empty(), "gemma3:4b");
        assertThat(selector.effectiveChatModelId(ctx)).contains("gpt-oss:20b");
    }

    @Test
    void ollamaProviderKeepsLegacyRagConfigModel() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());
        selector = new ChatGenerationModelSelector(catalog);
        OrchestrationLlmConfigScope.bind(
                ResolvedLlmConfig.uniform(
                        LlmProvider.OLLAMA_NATIVE,
                        "http://localhost:11434",
                        "gemma3:4b",
                        "mxbai-embed-large:latest",
                        null,
                        null,
                        0.1,
                        60_000,
                        null,
                        Map.of()));
        ExecutionContext ctx = ctxWithModels(Optional.empty(), "gemma3:4b");
        assertThat(selector.effectiveChatModelId(ctx)).contains("gemma3:4b");
    }

    @Test
    void emptyOverrideFallsBackToResolvedLlmConfig() {
        bindOpenAiConfig("gpt-oss:20b");
        ExecutionContext ctx = ctxWithModels(Optional.of("   "), "gemma3:4b");
        assertThat(selector.effectiveChatModelId(ctx)).contains("gpt-oss:20b");
    }

    @Test
    void invalidOverrideForProviderFailsClearly() {
        bindOpenAiConfig("gpt-oss:20b");
        ExecutionContext ctx = ctxWithModels(Optional.of("gemma3:4b"), "gemma3:4b");
        assertThrows(LlmConfigurationException.class, () -> selector.effectiveChatModelId(ctx));
    }

    private static void bindOpenAiConfig(String chatModel) {
        OrchestrationLlmConfigScope.bind(
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        chatModel,
                        "mxbai-embed-large:latest",
                        "OPENAI_COMPATIBLE_API_KEY",
                        null,
                        0.1,
                        60_000,
                        null,
                        Map.of()));
    }

    private static ExecutionContext ctxWithModels(Optional<String> chatOverride, String resolvedLlm) {
        RagConfig rag =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 5, 0.2, resolvedLlm, "emb", "cls", "SIMPLE");
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
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                chatOverride,
                Optional.empty(),
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
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }
}
