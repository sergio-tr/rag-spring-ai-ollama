package com.uniovi.rag.application.service.runtime.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.exception.llm.LlmExceptionTranslator;
import com.uniovi.rag.application.exception.llm.LlmProviderException;
import com.uniovi.rag.application.exception.llm.LlmSafeOperationLogger;
import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Single RAG workflow chat gateway: resolves per-turn LLM config, selects {@link LlmChatClient}, maps prompts to port DTOs.
 */
@Service
public class RagLlmChatInvoker {

    private static final Logger log = LoggerFactory.getLogger(RagLlmChatInvoker.class);

    private final LlmClientResolver llmClientResolver;
    private final ResolvedLlmConfigResolver resolvedLlmConfigResolver;
    private final ObjectMapper objectMapper;

    public RagLlmChatInvoker(
            LlmClientResolver llmClientResolver,
            ResolvedLlmConfigResolver resolvedLlmConfigResolver,
            ObjectMapper objectMapper) {
        this.llmClientResolver = llmClientResolver;
        this.resolvedLlmConfigResolver = resolvedLlmConfigResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * @param systemPrompt workflow / resolved RAG system prompt for this turn
     * @param userMessage  final user turn text
     */
    public String invoke(ExecutionContext ctx, String systemPrompt, String userMessage) {
        Objects.requireNonNull(ctx, "ctx");
        ResolvedLlmConfig config = effectiveConfig(ctx);
        LlmChatClient client = llmClientResolver.resolveChatClient(config);
        String model =
                ChatGenerationModelSelector.effectiveChatModelId(ctx).orElse(config.chatModel());
        String mergedSystem = mergeSystemPrompts(systemPrompt, config.systemPrompt());
        LlmChatRequest request =
                LlmChatRequest.of(
                        model,
                        mergedSystem,
                        userMessage != null ? userMessage : "",
                        config.temperature(),
                        config.timeoutMs(),
                        config.additionalParameters());
        long startedAt = System.nanoTime();
        LlmSafeOperationLogger.logStarted(log, "chat", config.provider(), model, config.baseUrl());
        try {
            String content = client.chat(request).content();
            LlmSafeOperationLogger.logCompleted(
                    log,
                    "chat",
                    config.provider(),
                    model,
                    config.baseUrl(),
                    elapsedMs(startedAt),
                    "OK");
            return content;
        } catch (Exception e) {
            LlmProviderException translated = LlmExceptionTranslator.translate(e, config, "chat", model);
            LlmSafeOperationLogger.logFailed(
                    log,
                    "chat",
                    config.provider(),
                    model,
                    config.baseUrl(),
                    elapsedMs(startedAt),
                    translated.failureKind().name(),
                    translated.publicMessage());
            throw translated;
        }
    }

    /** Effective config for the current orchestrated turn (bound at context factory). */
    public ResolvedLlmConfig effectiveConfig(ExecutionContext ctx) {
        return OrchestrationLlmConfigScope.current().orElseGet(() -> fallbackResolve(ctx));
    }

    private ResolvedLlmConfig fallbackResolve(ExecutionContext ctx) {
        UUID presetId = null;
        if (ctx.resolved() != null
                && ctx.resolved().provenance() != null
                && ctx.resolved().provenance().presetId() != null) {
            presetId = ctx.resolved().provenance().presetId();
        }
        JsonNode requestOverride = null;
        Optional<String> chatOverride = ctx.chatModelOverride();
        if (chatOverride.isPresent() && !chatOverride.get().isBlank()) {
            requestOverride = objectMapper.createObjectNode().put("llmModel", chatOverride.get().trim());
        }
        return resolvedLlmConfigResolver.resolve(
                ctx.userId(), ctx.projectId(), presetId, null, requestOverride);
    }

    static String mergeSystemPrompts(String ragSystemPrompt, String userLlmSystemPrompt) {
        String base = ragSystemPrompt != null ? ragSystemPrompt.trim() : "";
        String userLayer = userLlmSystemPrompt != null ? userLlmSystemPrompt.trim() : "";
        if (base.isEmpty()) {
            return userLayer;
        }
        if (userLayer.isEmpty()) {
            return base;
        }
        return base + "\n\n" + userLayer;
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
