package com.uniovi.rag.application.service.runtime.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.exception.llm.LlmExceptionTranslator;
import com.uniovi.rag.application.exception.llm.LlmProviderException;
import com.uniovi.rag.application.exception.llm.LlmSafeOperationLogger;
import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.config.llm.TaskLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.application.service.runtime.observability.RagLlmCallTelemetry;
import com.uniovi.rag.application.service.runtime.optimization.RagLlmCallBudgetEnforcer;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.llm.catalog.LlmModelUsageContext;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Single RAG workflow chat gateway: resolves per-turn LLM config, selects {@link LlmChatClient}, maps prompts to port DTOs.
 */
@Service
public class RagLlmChatInvoker {

    private static final Logger log = LoggerFactory.getLogger(RagLlmChatInvoker.class);

    private final LlmClientResolver llmClientResolver;
    private final ResolvedLlmConfigResolver resolvedLlmConfigResolver;
    private final TaskLlmConfigResolver taskLlmConfigResolver;
    private final ObjectMapper objectMapper;
    private final ChatGenerationModelSelector chatGenerationModelSelector;
    private final LlmModelCatalogPort modelCatalog;
    private final RagChatModelRoutingService chatModelRoutingService;

    public RagLlmChatInvoker(
            LlmClientResolver llmClientResolver,
            ResolvedLlmConfigResolver resolvedLlmConfigResolver,
            TaskLlmConfigResolver taskLlmConfigResolver,
            ObjectMapper objectMapper,
            ChatGenerationModelSelector chatGenerationModelSelector,
            LlmModelCatalogPort modelCatalog,
            RagChatModelRoutingService chatModelRoutingService) {
        this.llmClientResolver = llmClientResolver;
        this.resolvedLlmConfigResolver = resolvedLlmConfigResolver;
        this.taskLlmConfigResolver = taskLlmConfigResolver;
        this.objectMapper = objectMapper;
        this.chatGenerationModelSelector = chatGenerationModelSelector;
        this.modelCatalog = modelCatalog;
        this.chatModelRoutingService = chatModelRoutingService;
    }

    /**
     * @param systemPrompt workflow / resolved RAG system prompt for this turn
     * @param userMessage  final user turn text
     */
    public String invoke(ExecutionContext ctx, String systemPrompt, String userMessage) {
        Objects.requireNonNull(ctx, "ctx");
        ResolvedLlmConfig orchestrationBase = effectiveConfig(ctx);
        TaskLlmConfigResolver.FinalAnswerCallConfig finalAnswer =
                taskLlmConfigResolver.resolveFinalAnswer(ctx, orchestrationBase);
        ResolvedLlmConfig config = finalAnswer.effectiveConfig();
        LlmChatClient client = llmClientResolver.resolveChatClient(config);
        String requestedModel = finalAnswer.effectiveModel();
        RagChatModelRoutingService.RoutedChatModel routed =
                chatModelRoutingService.resolvePrimary(config.provider(), requestedModel, ctx);
        String model = routed.model();
        modelCatalog.assertUsable(
                config.provider(), model, LlmModelCapability.CHAT, LlmModelUsageContext.RAG_CHAT);
        int timeoutMs = RagLlmTimeoutPolicy.effectiveTimeoutMs(ctx, "PRIMARY", config.timeoutMs());
        String mergedSystem = mergeSystemPrompts(systemPrompt, config.systemPrompt());
        LlmChatRequest request =
                LlmChatRequest.of(
                        model,
                        mergedSystem,
                        userMessage != null ? userMessage : "",
                        config.temperature(),
                        timeoutMs,
                        config.additionalParameters());
        int inputChars =
                RagLlmCallTelemetry.approxChars(mergedSystem) + RagLlmChatInvoker.approxChars(userMessage);
        Integer retrievedChunks =
                ctx.advisorPackedContextSet().isPresent()
                        ? ctx.advisorPackedContextSet().get().totalBlockCount()
                        : null;
        long startedAt = System.nanoTime();
        String operation = TaskLlmTask.FINAL_ANSWER.operationName();
        LlmSafeOperationLogger.logStarted(log, "chat", config.provider(), model, config.baseUrl());
        RagLlmCallTelemetry.logStarted(
                ctx,
                operation,
                config.chatProvider(),
                model,
                inputChars,
                ctx.advisorPackedContextSet()
                        .map(p -> RagLlmCallTelemetry.approxChars(p.promptContextText()))
                        .orElse(0),
                retrievedChunks,
                config.temperature(),
                TaskLlmTask.FINAL_ANSWER.id(),
                finalAnswer.modelSource(),
                finalAnswer.paramSource(),
                finalAnswer.inheritModel(),
                config.additionalParameters());
        if (!RagLlmCallBudgetEnforcer.tryAllowPrimary(operation)) {
            throw new IllegalStateException("Primary LLM call blocked by budget policy");
        }
        try {
            String content = client.chat(request).content();
            if (log.isInfoEnabled()) {
                log.info(
                        "RAG_RAW_LLM_CONTENT provider={} model={} modelSource={} contentLen={} userTailLen={} raw={}",
                        config.provider(),
                        model,
                        finalAnswer.modelSource(),
                        content != null ? content.length() : -1,
                        userMessage != null ? userMessage.length() : 0,
                        content == null
                                ? "null"
                                : content.substring(0, Math.min(400, content.length())).replace("\n", "\\n"));
            }
            long latencyMs = elapsedMs(startedAt);
            LlmSafeOperationLogger.logCompleted(
                    log,
                    "chat",
                    config.provider(),
                    model,
                    config.baseUrl(),
                    latencyMs,
                    "OK");
            RagLlmCallTelemetry.logCompleted(
                    ctx,
                    operation,
                    config.chatProvider(),
                    model,
                    inputChars,
                    ctx.advisorPackedContextSet()
                            .map(p -> RagLlmCallTelemetry.approxChars(p.promptContextText()))
                            .orElse(0),
                    retrievedChunks,
                    latencyMs,
                    "OK",
                    TaskLlmTask.FINAL_ANSWER.id(),
                    finalAnswer.modelSource(),
                    finalAnswer.paramSource(),
                    finalAnswer.inheritModel(),
                    config.additionalParameters());
            RagLlmCallBudgetEnforcer.recordCompleted(operation);
            return content;
        } catch (Exception e) {
            long latencyMs = elapsedMs(startedAt);
            LlmProviderException translated = LlmExceptionTranslator.translate(e, config, "chat", model);
            LlmSafeOperationLogger.logFailed(
                    log,
                    "chat",
                    config.provider(),
                    model,
                    config.baseUrl(),
                    latencyMs,
                    translated.failureKind().name(),
                    translated.publicMessage());
            RagLlmCallTelemetry.logFailed(
                    ctx,
                    operation,
                    config.chatProvider(),
                    model,
                    inputChars,
                    ctx.advisorPackedContextSet()
                            .map(p -> RagLlmCallTelemetry.approxChars(p.promptContextText()))
                            .orElse(0),
                    latencyMs,
                    translated.failureKind().name(),
                    translated.publicMessage(),
                    TaskLlmTask.FINAL_ANSWER.id(),
                    finalAnswer.modelSource(),
                    finalAnswer.paramSource(),
                    finalAnswer.inheritModel(),
                    config.additionalParameters());
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

    private static int approxChars(@Nullable String text) {
        return text == null ? 0 : text.length();
    }
}
