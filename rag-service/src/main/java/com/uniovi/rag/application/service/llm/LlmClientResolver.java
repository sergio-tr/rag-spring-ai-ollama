package com.uniovi.rag.application.service.llm;

import com.uniovi.rag.application.exception.llm.LlmConfigurationException;
import com.uniovi.rag.application.exception.llm.UnsupportedEmbeddingProviderException;
import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Central provider switch for {@link LlmChatClient} and {@link LlmEmbeddingClient}.
 * Controllers and RAG workflows must not branch on {@link LlmProvider} directly.
 */
@Service
public class LlmClientResolver {

    private static final Logger log = LoggerFactory.getLogger(LlmClientResolver.class);

    private final LlmClientRegistryPort clientRegistry;

    public LlmClientResolver(LlmClientRegistryPort clientRegistry) {
        this.clientRegistry = clientRegistry;
    }

    public LlmChatClient resolveChatClient(ResolvedLlmConfig config) {
        Objects.requireNonNull(config, "config");
        LlmProvider provider = requireKnownProvider(config);
        validateCommon(config, "chat");
        logSafeResolution("chat", config);

        return switch (provider) {
            case OLLAMA_NATIVE -> {
                validateOllamaNative(config, "chat");
                yield clientRegistry.ollamaNativeChatClient();
            }
            case OPENAI_COMPATIBLE -> {
                validateOpenAiCompatible(config, "chat");
                yield clientRegistry.createOpenAiCompatibleChatClient(config);
            }
        };
    }

    public LlmEmbeddingClient resolveEmbeddingClient(ResolvedLlmConfig config) {
        Objects.requireNonNull(config, "config");
        LlmProvider provider = requireKnownProvider(config);
        validateCommon(config, "embedding");
        logSafeResolution("embedding", config);

        return switch (provider) {
            case OLLAMA_NATIVE -> {
                validateOllamaNative(config, "embedding");
                yield clientRegistry.ollamaNativeEmbeddingClient();
            }
            case OPENAI_COMPATIBLE ->
                    throw new UnsupportedEmbeddingProviderException(
                            LlmProvider.OPENAI_COMPATIBLE, config.chatModel(), config.baseUrl());
        };
    }

    private static LlmProvider requireKnownProvider(ResolvedLlmConfig config) {
        LlmProvider provider = config.provider();
        if (provider == null) {
            throw LlmConfigurationException.invalidField(
                    null, "resolve", config.chatModel(), config.baseUrl(), "LLM provider is not set in resolved configuration");
        }
        return provider;
    }

    private static void validateCommon(ResolvedLlmConfig config, String operation) {
        try {
            config.validate();
        } catch (IllegalStateException e) {
            throw LlmConfigurationException.invalidField(
                    config.provider(), operation, config.chatModel(), config.baseUrl(), e.getMessage());
        }
    }

    private static void validateOllamaNative(ResolvedLlmConfig config, String operation) {
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            throw LlmConfigurationException.invalidField(
                    LlmProvider.OLLAMA_NATIVE,
                    operation,
                    config.chatModel(),
                    config.baseUrl(),
                    "Ollama native LLM requires a non-blank llmBaseUrl");
        }
        if (config.chatModel() == null || config.chatModel().isBlank()) {
            throw LlmConfigurationException.invalidField(
                    LlmProvider.OLLAMA_NATIVE,
                    operation,
                    config.chatModel(),
                    config.baseUrl(),
                    "Ollama native LLM requires a non-blank llmModel");
        }
        if (config.embeddingModel() == null || config.embeddingModel().isBlank()) {
            throw LlmConfigurationException.invalidField(
                    LlmProvider.OLLAMA_NATIVE,
                    operation,
                    config.chatModel(),
                    config.baseUrl(),
                    "Ollama native LLM requires a non-blank embeddingModel");
        }
    }

    private static void validateOpenAiCompatible(ResolvedLlmConfig config, String operation) {
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            throw LlmConfigurationException.invalidField(
                    LlmProvider.OPENAI_COMPATIBLE,
                    operation,
                    config.chatModel(),
                    config.baseUrl(),
                    "OpenAI-compatible LLM requires a non-blank llmBaseUrl");
        }
        if (config.chatModel() == null || config.chatModel().isBlank()) {
            throw LlmConfigurationException.invalidField(
                    LlmProvider.OPENAI_COMPATIBLE,
                    operation,
                    config.chatModel(),
                    config.baseUrl(),
                    "OpenAI-compatible LLM requires a non-blank llmModel");
        }
        try {
            config.requireApiKeyEnvResolvable();
        } catch (IllegalStateException e) {
            String message = e.getMessage();
            if (message != null && message.contains("environment variable is not set")) {
                throw LlmConfigurationException.missingApiKeyEnv(
                        LlmProvider.OPENAI_COMPATIBLE,
                        operation,
                        config.chatModel(),
                        config.baseUrl(),
                        config.effectiveApiKeyEnv());
            }
            throw LlmConfigurationException.invalidField(
                    LlmProvider.OPENAI_COMPATIBLE,
                    operation,
                    config.chatModel(),
                    config.baseUrl(),
                    message);
        }
    }

    private static void logSafeResolution(String kind, ResolvedLlmConfig config) {
        log.info(
                "Resolved LLM {} client: provider={}, model={}, baseUrl={}",
                kind,
                config.provider(),
                config.chatModel(),
                config.baseUrl());
    }
}
