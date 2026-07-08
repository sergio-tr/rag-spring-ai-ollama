package com.uniovi.rag.application.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.config.llm.TaskLlmConfigResolver;
import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.application.service.runtime.llm.RagChatModelRoutingService;
import com.uniovi.rag.application.service.runtime.llm.RagLlmTimeoutPolicy;
import com.uniovi.rag.application.service.runtime.observability.RagLlmCallTelemetry;
import com.uniovi.rag.application.service.runtime.optimization.OptionalLlmCallBudgetSkippedException;
import com.uniovi.rag.application.service.runtime.optimization.RagLlmCallBudgetEnforcer;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Provider-aware secondary LLM calls (NER, error composer, rewrite, condense). Uses {@link LlmClientResolver} -
 * never the Spring AI default Ollama {@code ChatClient} bean.
 */
@Service
public class ProviderAwareSecondaryLlmExecutor {

    /** Low-variance default for deterministic secondary tasks (rewrite, condense) when config omits temperature. */
    public static final double SECONDARY_TASK_DEFAULT_TEMPERATURE = 0.0;

    private static final Logger log = LoggerFactory.getLogger(ProviderAwareSecondaryLlmExecutor.class);

    private final LlmClientResolver llmClientResolver;
    private final ResolvedLlmConfigResolver resolvedLlmConfigResolver;
    private final TaskLlmConfigResolver taskLlmConfigResolver;
    private final ChatGenerationModelSelector chatGenerationModelSelector;
    private final ObjectMapper objectMapper;
    private final RagChatModelRoutingService chatModelRoutingService;

    public ProviderAwareSecondaryLlmExecutor(
            LlmClientResolver llmClientResolver,
            ResolvedLlmConfigResolver resolvedLlmConfigResolver,
            TaskLlmConfigResolver taskLlmConfigResolver,
            ChatGenerationModelSelector chatGenerationModelSelector,
            ObjectMapper objectMapper,
            RagChatModelRoutingService chatModelRoutingService) {
        this.llmClientResolver = llmClientResolver;
        this.resolvedLlmConfigResolver = resolvedLlmConfigResolver;
        this.taskLlmConfigResolver = taskLlmConfigResolver;
        this.chatGenerationModelSelector = chatGenerationModelSelector;
        this.objectMapper = objectMapper;
        this.chatModelRoutingService = chatModelRoutingService;
    }

    public String complete(String operation, @Nullable String systemPrompt, String userPrompt) {
        TaskLlmConfigResolver.SecondaryCallConfig call =
                taskLlmConfigResolver.resolveSecondaryCall(null, null, operation, null, null);
        return completeInternal(
                null,
                call.effectiveConfig(),
                call.effectiveModel(),
                operation,
                systemPrompt,
                userPrompt,
                call.effectiveTemperature(),
                call.taskOverrideApplied());
    }

    /**
     * Context-aware secondary call with catalog-validated model selection (rewrite, condense).
     *
     * @param temperatureOverride when non-null and task override has no temperature, used instead of resolved config
     */
    public String complete(
            ExecutionContext ctx,
            String operation,
            @Nullable String systemPrompt,
            String userPrompt,
            @Nullable Double temperatureOverride) {
        Objects.requireNonNull(ctx, "ctx");
        String selectorModel = chatGenerationModelSelector.effectiveChatModelId(ctx).orElse(null);
        TaskLlmConfigResolver.SecondaryCallConfig call =
                taskLlmConfigResolver.resolveSecondaryCall(ctx, operation, temperatureOverride, selectorModel);
        String model = call.effectiveModel();
        if (!call.taskOverrideApplied() && !call.secondaryModelApplied() && selectorModel != null && !selectorModel.isBlank()) {
            model = selectorModel.trim();
        }
        return completeInternal(
                ctx,
                call.effectiveConfig(),
                model,
                operation,
                systemPrompt,
                userPrompt,
                call.effectiveTemperature(),
                call.taskOverrideApplied());
    }

    private String completeInternal(
            @Nullable ExecutionContext ctx,
            ResolvedLlmConfig config,
            String model,
            String operation,
            @Nullable String systemPrompt,
            String userPrompt,
            @Nullable Double temperature,
            boolean taskOverrideApplied) {
        if (!RagLlmCallBudgetEnforcer.tryAllowSecondary(operation)) {
            throw new OptionalLlmCallBudgetSkippedException(operation);
        }
        log.debug(
                "Secondary LLM call: operation={} provider={} model={} baseUrl={} temperature={} taskOverride={}",
                operation,
                config.chatProvider(),
                model,
                config.baseUrl(),
                temperature,
                taskOverrideApplied);

        int inputChars =
                RagLlmCallTelemetry.approxChars(systemPrompt) + RagLlmCallTelemetry.approxChars(userPrompt);
        Integer retrievedChunks = null;
        if (ctx != null) {
            Optional<PackedContextSet> packed = ctx.advisorPackedContextSet();
            if (packed.isPresent()) {
                retrievedChunks = packed.get().totalBlockCount();
            }
        }
        long startedAt = System.nanoTime();
        RagLlmCallTelemetry.logStarted(
                ctx,
                operation,
                config.chatProvider(),
                model,
                inputChars,
                0,
                retrievedChunks,
                temperature);
        String routedModel = model;
        try {
            routedModel = chatModelRoutingService.resolveSecondary(config.chatProvider(), model, ctx);
            int timeoutMs = RagLlmTimeoutPolicy.effectiveTimeoutMs(ctx, "SECONDARY", config.timeoutMs());
            LlmChatClient client = llmClientResolver.resolveChatClient(config);
            LlmChatResponse response =
                    client.chat(
                            LlmChatRequest.of(
                                    routedModel,
                                    systemPrompt,
                                    userPrompt,
                                    temperature,
                                    timeoutMs,
                                    config.additionalParameters()));
            String content = response != null ? response.content() : null;
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Empty secondary LLM response for operation=" + operation);
            }
            RagLlmCallTelemetry.logCompleted(
                    ctx,
                    operation,
                    config.chatProvider(),
                    routedModel,
                    inputChars,
                    0,
                    retrievedChunks,
                    elapsedMs(startedAt),
                    "OK");
            RagLlmCallBudgetEnforcer.recordCompleted(operation);
            return content.trim();
        } catch (RuntimeException e) {
            RagLlmCallTelemetry.logFailed(
                    ctx,
                    operation,
                    config.chatProvider(),
                    routedModel,
                    inputChars,
                    0,
                    elapsedMs(startedAt),
                    e.getClass().getSimpleName(),
                    e.getMessage());
            throw e;
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    public ResolvedLlmConfig effectiveConfig() {
        return OrchestrationLlmConfigScope.current()
                .orElseGet(() -> resolvedLlmConfigResolver.resolve(null, null, null));
    }

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
}
