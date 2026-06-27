package com.uniovi.rag.application.exception.llm;

import com.uniovi.rag.domain.llm.LlmProvider;

/** Invalid or incomplete LLM configuration (missing env var name, blank base URL, unknown provider). */
public class LlmConfigurationException extends LlmProviderException {

    public LlmConfigurationException(
            LlmProvider provider,
            String operation,
            String model,
            String baseUrl,
            String publicMessage,
            String detail,
            Throwable cause) {
        super(
                LlmFailureKind.CONFIGURATION,
                provider,
                operation,
                model,
                baseUrl,
                publicMessage,
                detail,
                cause);
    }

    public static LlmConfigurationException missingApiKeyEnv(
            LlmProvider provider, String operation, String model, String baseUrl, String envVarName) {
        return new LlmConfigurationException(
                provider,
                operation,
                model,
                baseUrl,
                "API key environment variable is not set or empty: "
                        + envVarName
                        + " (configure the secret externally; never store keys in the database)",
                envVarName,
                null);
    }

    public static LlmConfigurationException invalidField(
            LlmProvider provider, String operation, String model, String baseUrl, String message) {
        return new LlmConfigurationException(provider, operation, model, baseUrl, message, null, null);
    }
}
