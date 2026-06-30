package com.uniovi.rag.application.exception.llm;

import com.uniovi.rag.application.service.llm.LlmClientResolutionException;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleLlmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central mapping from infrastructure LLM exceptions to application-layer {@link LlmProviderException}.
 */
public final class LlmExceptionTranslator {

    private LlmExceptionTranslator() {}

    public static LlmProviderException translate(
            Throwable throwable, ResolvedLlmConfig config, String operation, String model) {
        if (throwable instanceof LlmProviderException providerException) {
            return providerException;
        }
        if (throwable instanceof LlmClientResolutionException resolutionException) {
            return toConfiguration(resolutionException, config, operation, model);
        }
        if (throwable instanceof OpenAiCompatibleLlmException openAiException) {
            return fromOpenAiCompatible(openAiException, config, operation, model);
        }
        LlmProvider provider = config != null ? config.provider() : null;
        String baseUrl = config != null ? config.baseUrl() : null;
        return LlmRemoteFailures.remoteHttp(
                provider != null ? provider : LlmProvider.OPENAI_COMPATIBLE,
                operation,
                model,
                baseUrl,
                0,
                throwable != null ? throwable.getMessage() : "unknown LLM failure");
    }

    private static LlmConfigurationException toConfiguration(
            LlmClientResolutionException ex, ResolvedLlmConfig config, String operation, String model) {
        LlmProvider provider = config != null ? config.provider() : null;
        String baseUrl = config != null ? config.baseUrl() : null;
        return new LlmConfigurationException(
                provider, operation, model, baseUrl, ex.getMessage(), null, ex);
    }

    private static LlmProviderException fromOpenAiCompatible(
            OpenAiCompatibleLlmException ex, ResolvedLlmConfig config, String operation, String model) {
        LlmProvider provider = LlmProvider.OPENAI_COMPATIBLE;
        String baseUrl = config != null ? config.baseUrl() : null;
        String effectiveModel = model != null ? model : config != null ? config.chatModel() : null;
        Integer timeoutMs = config != null ? config.timeoutMs() : null;

        return switch (ex.kind()) {
            case MISCONFIGURED -> new LlmConfigurationException(
                    provider, operation, effectiveModel, baseUrl, ex.getMessage(), null, ex);
            case UNAUTHORIZED -> LlmRemoteFailures.unauthorized(
                    provider, operation, effectiveModel, baseUrl, extractHttpStatus(ex.getMessage(), 401));
            case ENDPOINT_NOT_FOUND -> LlmRemoteFailures.endpointNotFound(
                    provider, operation, effectiveModel, baseUrl, extractHttpStatus(ex.getMessage(), 404));
            case TIMEOUT -> new LlmTimeoutException(provider, operation, effectiveModel, baseUrl, timeoutMs, ex);
            case CONNECTION_FAILED -> LlmRemoteFailures.connectionFailed(
                    provider, operation, effectiveModel, baseUrl, ex);
            case INVALID_MODEL -> LlmRemoteFailures.invalidModel(
                    provider, operation, effectiveModel, baseUrl, ex.getMessage());
            case INVALID_RESPONSE -> LlmRemoteFailures.invalidResponse(
                    provider, operation, effectiveModel, baseUrl, ex.getMessage());
            case HTTP_ERROR -> LlmRemoteFailures.remoteHttp(
                    provider, operation, effectiveModel, baseUrl, extractHttpStatus(ex.getMessage(), 0), ex.getMessage());
        };
    }

    private static int extractHttpStatus(String message, int defaultStatus) {
        if (message == null) {
            return defaultStatus;
        }
        Matcher matcher = Pattern.compile("HTTP (\\d{3})").matcher(message);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return defaultStatus;
            }
        }
        return defaultStatus;
    }
}
