package com.uniovi.rag.application.exception.llm;

import com.uniovi.rag.domain.llm.LlmProvider;

/** LLM remote call exceeded the configured timeout. */
public class LlmTimeoutException extends LlmProviderException {

    public LlmTimeoutException(
            LlmProvider provider,
            String operation,
            String model,
            String baseUrl,
            Integer timeoutMs,
            Throwable cause) {
        super(
                LlmFailureKind.TIMEOUT,
                provider,
                operation,
                model,
                baseUrl,
                "LLM request timed out"
                        + (timeoutMs != null ? " after " + timeoutMs + " ms" : "")
                        + " (provider="
                        + provider
                        + ")",
                timeoutMs != null ? "timeoutMs=" + timeoutMs : null,
                cause);
    }
}
