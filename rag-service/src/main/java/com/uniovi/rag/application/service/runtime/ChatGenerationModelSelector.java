package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogDefaults;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.llm.catalog.LlmModelUsageContext;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves the effective chat model for a turn using the central catalog and resolved LLM provider config.
 * Legacy {@link RagConfig#llmModel()} is only considered for {@link LlmProvider#OLLAMA_NATIVE} when no
 * provider-validated model is available from {@link ResolvedLlmConfig#chatModel()}.
 */
@Service
public class ChatGenerationModelSelector {

    private final LlmModelCatalogPort modelCatalog;

    public ChatGenerationModelSelector(LlmModelCatalogPort modelCatalog) {
        this.modelCatalog = modelCatalog;
    }

    /**
     * Request-scoped {@link ExecutionContext#chatModelOverride()} wins when valid for the effective provider;
     * otherwise uses {@link ResolvedLlmConfig#chatModel()} validated against the provider, then (Ollama only)
     * legacy {@link RagConfig#llmModel()}.
     */
    public Optional<String> effectiveChatModelId(ExecutionContext ctx) {
        if (ctx == null) {
            return Optional.empty();
        }
        ResolvedLlmConfig llmConfig = OrchestrationLlmConfigScope.current().orElse(null);
        LlmProvider provider = llmConfig != null ? llmConfig.chatProvider() : LlmProvider.OLLAMA_NATIVE;

        Optional<String> override = normalizedOverride(ctx.chatModelOverride());
        if (override.isPresent()) {
            modelCatalog.assertUsable(
                    provider, override.get(), LlmModelCapability.CHAT, LlmModelUsageContext.USER_SELECTION);
            return override;
        }

        Optional<String> fromResolved =
                catalogValidatedModel(provider, llmConfig != null ? llmConfig.chatModel() : null);
        if (fromResolved.isPresent()) {
            return fromResolved;
        }

        if (provider == LlmProvider.OLLAMA_NATIVE) {
            RagConfig rag = ctx.resolved() != null ? ctx.resolved().toRagConfig() : null;
            Optional<String> legacy = catalogValidatedModel(provider, rag != null ? rag.llmModel() : null);
            if (legacy.isPresent()) {
                return legacy;
            }
        }

        return providerDefaultChatModel(provider);
    }

    private Optional<String> catalogValidatedModel(LlmProvider provider, String modelId) {
        if (provider == null || modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = modelId.trim();
        return modelCatalog.find(provider, trimmed, LlmModelCapability.CHAT).map(entry -> trimmed);
    }

    private Optional<String> providerDefaultChatModel(LlmProvider provider) {
        LlmCatalogDefaults defaults = modelCatalog.resolveSystemDefaults(provider);
        if (defaults.defaultChatModel() != null && !defaults.defaultChatModel().isBlank()) {
            return Optional.of(defaults.defaultChatModel().trim());
        }
        return Optional.empty();
    }

    private static Optional<String> normalizedOverride(Optional<String> chatModelOverride) {
        if (chatModelOverride == null || chatModelOverride.isEmpty()) {
            return Optional.empty();
        }
        String trimmed = chatModelOverride.get().trim();
        return trimmed.isBlank() ? Optional.empty() : Optional.of(trimmed);
    }
}
