package com.uniovi.rag.application.service.llm;

import com.uniovi.rag.application.exception.llm.LlmExceptionTranslator;
import com.uniovi.rag.application.exception.llm.LlmProviderException;
import com.uniovi.rag.application.exception.llm.LlmRemoteFailures;
import com.uniovi.rag.application.exception.llm.LlmSafeOperationLogger;
import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleLlmChatClient;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manual LLM connectivity probe (admin-only). Not invoked on normal RAG/chat requests.
 */
@Service
public class LlmManualHealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(LlmManualHealthCheckService.class);

    private final ResolvedLlmConfigResolver configResolver;
    private final LlmClientResolver llmClientResolver;
    private final LlmClientRegistryPort clientRegistry;
    private final OllamaApiClient ollamaApiClient;

    public LlmManualHealthCheckService(
            ResolvedLlmConfigResolver configResolver,
            LlmClientResolver llmClientResolver,
            LlmClientRegistryPort clientRegistry,
            OllamaApiClient ollamaApiClient) {
        this.configResolver = configResolver;
        this.llmClientResolver = llmClientResolver;
        this.clientRegistry = clientRegistry;
        this.ollamaApiClient = ollamaApiClient;
    }

    public LlmHealthCheckResult checkApplicationDefaults() {
        ResolvedLlmConfig config = configResolver.resolve(null, null, null, null, null);
        return checkResolved(config);
    }

    public LlmHealthCheckResult checkResolved(ResolvedLlmConfig config) {
        return checkResolvedWithTimeout(config, null);
    }

    /**
     * Lightweight chat probe for fail-fast preflight. Uses {@code max_tokens=1} style health prompts.
     *
     * @param timeoutMs when null, uses resolved config timeout (capped by caller)
     */
    public LlmHealthCheckResult probeChatWithTimeout(UUID userId, String chatModelOverride, int timeoutMs) {
        Optional<String> override =
                chatModelOverride != null && !chatModelOverride.isBlank()
                        ? Optional.of(chatModelOverride.trim())
                        : Optional.empty();
        ResolvedLlmConfig config =
                configResolver.resolveForOrchestratedExecute(userId, null, null, null, override);
        return checkResolvedWithTimeout(config, timeoutMs);
    }

    private LlmHealthCheckResult checkResolvedWithTimeout(ResolvedLlmConfig config, Integer timeoutOverrideMs) {
        String operation = "health-check";
        long startedAt = System.nanoTime();
        LlmSafeOperationLogger.logStarted(
                log, operation, config.provider(), config.chatModel(), config.baseUrl());
        try {
            LlmHealthCheckResult result =
                    switch (config.provider()) {
                        case OLLAMA_NATIVE -> checkOllama(config, startedAt);
                        case OPENAI_COMPATIBLE -> checkOpenAiCompatible(config, startedAt, timeoutOverrideMs);
                    };
            LlmSafeOperationLogger.logCompleted(
                    log,
                    operation,
                    config.provider(),
                    config.chatModel(),
                    config.baseUrl(),
                    result.latencyMs(),
                    result.status());
            return result;
        } catch (Exception e) {
            LlmProviderException translated =
                    LlmExceptionTranslator.translate(e, config, operation, config.chatModel());
            long latencyMs = elapsedMs(startedAt);
            LlmSafeOperationLogger.logFailed(
                    log,
                    operation,
                    config.provider(),
                    config.chatModel(),
                    config.baseUrl(),
                    latencyMs,
                    translated.failureKind().name(),
                    translated.publicMessage());
            return LlmHealthCheckResult.failure(config, latencyMs, translated.publicMessage());
        }
    }

    private LlmHealthCheckResult checkOllama(ResolvedLlmConfig config, long startedAt)
            throws IOException, InterruptedException {
        llmClientResolver.resolveChatClient(config);
        boolean reachable = ollamaApiClient.ping();
        long latencyMs = elapsedMs(startedAt);
        if (!reachable) {
            throw LlmRemoteFailures.ollamaUnavailable(
                    "health-check", config.chatModel(), config.baseUrl());
        }
        return LlmHealthCheckResult.success(config, latencyMs);
    }

    private LlmHealthCheckResult checkOpenAiCompatible(ResolvedLlmConfig config, long startedAt) {
        return checkOpenAiCompatible(config, startedAt, null);
    }

    private LlmHealthCheckResult checkOpenAiCompatible(
            ResolvedLlmConfig config, long startedAt, Integer timeoutOverrideMs) {
        llmClientResolver.resolveChatClient(config);
        LlmChatClient client = clientRegistry.createOpenAiCompatibleChatClient(config);
        int configured = config.timeoutMs() != null && config.timeoutMs() > 0 ? config.timeoutMs() : 60_000;
        int timeoutMs = timeoutOverrideMs != null && timeoutOverrideMs > 0 ? timeoutOverrideMs : configured;
        LlmChatRequest probe =
                LlmChatRequest.of(
                        config.chatModel(),
                        OpenAiCompatibleLlmChatClient.HEALTH_PROBE_SYSTEM_PROMPT,
                        OpenAiCompatibleLlmChatClient.HEALTH_PROBE_USER_PROMPT,
                        config.temperature(),
                        timeoutMs,
                        Map.of());
        LlmChatResponse response = client.chat(probe);
        long latencyMs = elapsedMs(startedAt);
        if (response.content() == null || response.content().isBlank()) {
            throw LlmRemoteFailures.invalidResponse(
                    LlmProvider.OPENAI_COMPATIBLE,
                    "health-check",
                    config.chatModel(),
                    config.baseUrl(),
                    "empty assistant message");
        }
        return LlmHealthCheckResult.success(config, latencyMs);
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    public record LlmHealthCheckResult(
            LlmProvider provider,
            String model,
            String baseUrl,
            String operation,
            boolean healthy,
            long latencyMs,
            String status,
            String message) {

        static LlmHealthCheckResult success(ResolvedLlmConfig config, long latencyMs) {
            return new LlmHealthCheckResult(
                    config.provider(),
                    config.chatModel(),
                    config.baseUrl(),
                    "health-check",
                    true,
                    latencyMs,
                    "UP",
                    "LLM probe succeeded");
        }

        static LlmHealthCheckResult failure(ResolvedLlmConfig config, long latencyMs, String message) {
            return new LlmHealthCheckResult(
                    config.provider(),
                    config.chatModel(),
                    config.baseUrl(),
                    "health-check",
                    false,
                    latencyMs,
                    "DOWN",
                    message);
        }
    }
}
