package com.uniovi.rag.application.exception.llm;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;

/**
 * Safe structured logging for LLM operations. Never logs Authorization headers, API keys, or env secret values.
 */
public final class LlmSafeOperationLogger {

    private LlmSafeOperationLogger() {}

    /**
     * Single-line summary of {@link ResolvedLlmConfig} for startup and pre-RAG verification. Logs provider and model at
     * INFO; baseUrl only at DEBUG — never {@code apiKeyEnv}, {@code secretName}, Bearer tokens, or resolved secrets.
     */
    public static void logResolvedConfig(Logger log, ResolvedLlmConfig config) {
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(config, "config");
        log.info(formatResolvedConfigSummary(config));
        log.debug("Resolved LLM config baseUrl={}", sanitizeLogValue(config.baseUrl()));
    }

    public static String formatResolvedConfigSummary(ResolvedLlmConfig config) {
        Objects.requireNonNull(config, "config");
        return "Resolved LLM config: chatProvider="
                + config.chatProvider()
                + " chatModel="
                + sanitizeLogValue(config.chatModel())
                + " embeddingProvider="
                + config.embeddingProvider()
                + " embeddingModel="
                + sanitizeLogValue(config.embeddingModel());
    }

    static String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("bearer") || lower.startsWith("authorization:")) {
            return "[redacted]";
        }
        if (trimmed.regionMatches(true, 0, "sk-", 0, 3)) {
            return "[redacted]";
        }
        return trimmed;
    }

    public static void logStarted(Logger log, String operation, LlmProvider provider, String model, String baseUrl) {
        log.info("LLM operation started: operation={} provider={} model={}", operation, provider, model);
        log.debug("LLM operation started baseUrl={}", sanitizeLogValue(baseUrl));
    }

    public static void logCompleted(
            Logger log,
            String operation,
            LlmProvider provider,
            String model,
            String baseUrl,
            long latencyMs,
            String status) {
        log.info(
                "LLM operation completed: operation={} provider={} model={} latencyMs={} status={}",
                operation,
                provider,
                model,
                latencyMs,
                status);
        log.debug("LLM operation completed baseUrl={}", sanitizeLogValue(baseUrl));
    }

    public static void logFailed(
            Logger log,
            String operation,
            LlmProvider provider,
            String model,
            String baseUrl,
            long latencyMs,
            String status,
            String publicMessage) {
        log.warn(
                "LLM operation failed: operation={} provider={} model={} latencyMs={} status={} message={}",
                operation,
                provider,
                model,
                latencyMs,
                status,
                publicMessage);
        log.debug("LLM operation failed baseUrl={}", sanitizeLogValue(baseUrl));
    }
}
