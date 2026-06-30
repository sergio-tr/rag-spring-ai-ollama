package com.uniovi.rag.application.service.llm;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Provider-aware secondary LLM calls (NER, error composer, metadata fallbacks). Uses {@link LlmClientResolver} —
 * never the Spring AI default Ollama {@code ChatClient} bean.
 */
@Service
public class ProviderAwareSecondaryLlmExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProviderAwareSecondaryLlmExecutor.class);

    private final LlmClientResolver llmClientResolver;
    private final ResolvedLlmConfigResolver resolvedLlmConfigResolver;

    public ProviderAwareSecondaryLlmExecutor(
            LlmClientResolver llmClientResolver, ResolvedLlmConfigResolver resolvedLlmConfigResolver) {
        this.llmClientResolver = llmClientResolver;
        this.resolvedLlmConfigResolver = resolvedLlmConfigResolver;
    }

    public String complete(String operation, @Nullable String systemPrompt, String userPrompt) {
        ResolvedLlmConfig config = effectiveConfig();
        log.debug(
                "Secondary LLM call: operation={} provider={} model={} baseUrl={}",
                operation,
                config.chatProvider(),
                config.chatModel(),
                config.baseUrl());

        LlmChatClient client = llmClientResolver.resolveChatClient(config);
        LlmChatResponse response =
                client.chat(
                        LlmChatRequest.of(
                                config.chatModel(),
                                systemPrompt,
                                userPrompt,
                                config.temperature(),
                                config.timeoutMs(),
                                config.additionalParameters()));
        String content = response != null ? response.content() : null;
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Empty secondary LLM response for operation=" + operation);
        }
        return content.trim();
    }

    public ResolvedLlmConfig effectiveConfig() {
        return OrchestrationLlmConfigScope.current()
                .orElseGet(() -> resolvedLlmConfigResolver.resolve(null, null, null));
    }
}
