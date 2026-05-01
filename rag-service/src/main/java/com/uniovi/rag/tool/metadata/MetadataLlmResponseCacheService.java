package com.uniovi.rag.tool.metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Holds {@link Cacheable} LLM calls so they are invoked through the Spring proxy (not {@code this}).
 */
@Service
public class MetadataLlmResponseCacheService {

    private static final Logger log = LoggerFactory.getLogger(MetadataLlmResponseCacheService.class);

    private final ChatClient chatClient;

    public MetadataLlmResponseCacheService(ChatClient chatClient) {
        this.chatClient = chatClient;
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

    private void sleepBeforeRetryIfNeeded(int attempt) throws InterruptedException {
        if (attempt > 0) {
            log.debug("Retry attempt {} for LLM call", attempt);
            Thread.sleep(500L * attempt);
        }
    }

    private String invokeLlm(String prompt) {
        return chatClient.prompt().user(prompt).call().content();
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
