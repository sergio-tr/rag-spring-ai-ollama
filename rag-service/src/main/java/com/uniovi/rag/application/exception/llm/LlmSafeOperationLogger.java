package com.uniovi.rag.application.exception.llm;

import com.uniovi.rag.domain.llm.LlmProvider;
import org.slf4j.Logger;

/**
 * Safe structured logging for LLM operations. Never logs Authorization headers, API keys, or env secret values.
 */
public final class LlmSafeOperationLogger {

    private LlmSafeOperationLogger() {}

    public static void logStarted(Logger log, String operation, LlmProvider provider, String model, String baseUrl) {
        log.info(
                "LLM operation started: operation={} provider={} model={} baseUrl={}",
                operation,
                provider,
                model,
                baseUrl);
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
                "LLM operation completed: operation={} provider={} model={} baseUrl={} latencyMs={} status={}",
                operation,
                provider,
                model,
                baseUrl,
                latencyMs,
                status);
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
                "LLM operation failed: operation={} provider={} model={} baseUrl={} latencyMs={} status={} message={}",
                operation,
                provider,
                model,
                baseUrl,
                latencyMs,
                status,
                publicMessage);
    }
}
