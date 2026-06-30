package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Holds {@link Cacheable} LLM calls so they are invoked through the Spring proxy (not {@code this}).
 * Resolves {@link LlmChatClient} via {@link LlmClientResolver} from the effective {@link ResolvedLlmConfig}
 * (orchestration scope when bound, otherwise application defaults) — never the Spring default Ollama {@code ChatClient}.
 */
@Service
public class MetadataLlmResponseCacheService {

    private static final Logger log = LoggerFactory.getLogger(MetadataLlmResponseCacheService.class);

    private final LlmClientResolver llmClientResolver;
    private final ResolvedLlmConfigResolver resolvedLlmConfigResolver;

    public MetadataLlmResponseCacheService(
            LlmClientResolver llmClientResolver, ResolvedLlmConfigResolver resolvedLlmConfigResolver) {
        this.llmClientResolver = llmClientResolver;
        this.resolvedLlmConfigResolver = resolvedLlmConfigResolver;
    }

    /**
     * Cached LLM response with error handling and validation.
     * Returns empty string if LLM call fails or response is empty.
     */
    @Cacheable(value = "llmResponses", key = "#prompt.hashCode()")
    public String getCachedResponse(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            log.warn("Empty prompt provided to getCachedResponse");
            return "";
        }

        int maxRetries = 2;
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                sleepBeforeRetryIfNeeded(attempt);
                String response = invokeLlm(prompt);
                if (response != null && !response.trim().isEmpty()) {
                    return response.strip();
                }
                log.warn("Empty response from LLM in getCachedResponse (attempt {})", attempt + 1);
                if (attempt >= maxRetries) {
                    return "";
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrupted in getCachedResponse", e);
                return "";
            } catch (NullPointerException | IllegalArgumentException e) {
                log.error("{} in getCachedResponse (attempt {}): {}", e.getClass().getSimpleName(), attempt + 1, e.getMessage(), e);
                return "";
            } catch (Exception e) {
                lastException = e;
                logLlmExceptionByKind(attempt + 1, e);
            }
        }

        if (lastException != null) {
            log.error("Failed to get LLM response after {} attempts. Last error: {}",
                    maxRetries + 1, lastException.getMessage(), lastException);
        }
        return "";
    }

    /**
     * Effective LLM config for metadata enrichment: per-turn orchestration binding when present,
     * otherwise application defaults (same semantics as {@code ProviderAwareEmbeddingService}).
     */
    public ResolvedLlmConfig resolveEffectiveConfig() {
        return OrchestrationLlmConfigScope.current()
                .orElseGet(() -> resolvedLlmConfigResolver.resolve(null, null, null));
    }

    private void sleepBeforeRetryIfNeeded(int attempt) throws InterruptedException {
        if (attempt > 0) {
            log.debug("Retry attempt {} for LLM call", attempt);
            Thread.sleep(500L * attempt);
        }
    }

    private String invokeLlm(String prompt) {
        ResolvedLlmConfig config = resolveEffectiveConfig();
        LlmChatClient client = llmClientResolver.resolveChatClient(config);
        LlmChatRequest request =
                LlmChatRequest.of(
                        config.chatModel(),
                        null,
                        prompt,
                        config.temperature(),
                        config.timeoutMs(),
                        config.additionalParameters());
        return client.chat(request).content();
    }

    private static void logLlmExceptionByKind(int attemptOneBased, Exception e) {
        String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String className = e.getClass().getName();
        boolean transientFailure = errorMsg.contains("timeout")
                || errorMsg.contains("timed out")
                || className.contains("Timeout")
                || errorMsg.contains("connection")
                || errorMsg.contains("network")
                || errorMsg.contains("socket")
                || className.contains("Connection")
                || className.contains("Network");
        if (transientFailure) {
            log.warn("Timeout/network error in getCachedResponse (attempt {}): {}", attemptOneBased, e.getMessage());
        } else {
            log.error("Error in getCachedResponse (attempt {}): {}", attemptOneBased, e.getMessage(), e);
        }
    }
}
