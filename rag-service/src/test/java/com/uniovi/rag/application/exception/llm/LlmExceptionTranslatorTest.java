package com.uniovi.rag.application.exception.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uniovi.rag.application.service.llm.LlmClientResolutionException;
import com.uniovi.rag.domain.exception.ErrorCode;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleLlmException;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LlmExceptionTranslatorTest {

    @Test
    void unavailableConfiguredModelMapsToClearErrorCode() {
        LlmConfigurationException source =
                LlmConfigurationException.invalidField(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "catalog",
                        "unavailable-model",
                        null,
                        "Model 'unavailable-model' is not available in the catalog");

        LlmProviderException translated =
                LlmExceptionTranslator.translate(source, openAiConfig(), "chat", "unavailable-model");

        assertInstanceOf(LlmConfigurationException.class, translated);
        assertEquals(ErrorCode.LLM_MISCONFIGURED, translated.errorCode());
    }

    @Test
    void unconfiguredModelMapsToClearErrorCode() {
        LlmConfigurationException source =
                LlmConfigurationException.invalidField(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "catalog",
                        "unknown-model",
                        null,
                        "Model 'unknown-model' is not registered for provider OPENAI_COMPATIBLE");

        LlmProviderException translated =
                LlmExceptionTranslator.translate(source, openAiConfig(), "chat", "unknown-model");

        assertEquals(ErrorCode.LLM_MISCONFIGURED, translated.errorCode());
        assertTrue(translated.publicMessage().contains("unknown-model"));
    }

    @Test
    void incompatibleEmbeddingModelMapsToClearErrorCode() {
        LlmConfigurationException source =
                LlmConfigurationException.invalidField(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "embedding",
                        "gpt-oss:20b",
                        null,
                        "Model 'gpt-oss:20b' is not registered for provider OPENAI_COMPATIBLE with capability EMBEDDING");

        LlmProviderException translated =
                LlmExceptionTranslator.translate(source, openAiConfig(), "embedding", "gpt-oss:20b");

        assertEquals(ErrorCode.LLM_MISCONFIGURED, translated.errorCode());
        assertTrue(translated.publicMessage().contains("EMBEDDING"));
    }

    @Test
    void missingApiKeyEnvMapsToLlmMisconfigured() {
        translate_missingApiKey_mapsToConfigurationException();
    }

    @Test
    void invalidLiteLlmModelMapsToInvalidModel() {
        LlmConfigurationException source =
                LlmConfigurationException.invalidField(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "catalog",
                        "gemma3:4b",
                        "http://litellm:4000",
                        "Model 'gemma3:4b' is registered for OLLAMA_NATIVE only");

        LlmProviderException translated =
                LlmExceptionTranslator.translate(source, openAiConfig(), "chat", "gemma3:4b");

        assertEquals(ErrorCode.LLM_MISCONFIGURED, translated.errorCode());
        assertTrue(translated.publicMessage().contains("gemma3:4b"));
    }

    @Test
    void translate_missingApiKey_mapsToConfigurationException() {
        ResolvedLlmConfig config = openAiConfig();
        OpenAiCompatibleLlmException source =
                OpenAiCompatibleLlmException.missingApiKey("LITELLM_API_KEY");

        LlmProviderException translated = LlmExceptionTranslator.translate(source, config, "chat", "gpt-4o");

        assertInstanceOf(LlmConfigurationException.class, translated);
        assertEquals(ErrorCode.LLM_MISCONFIGURED, translated.errorCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, translated.httpStatus());
        assertTrue(translated.publicMessage().contains("LITELLM_API_KEY"));
    }

    @Test
    void translate_unauthorized_mapsToUnauthorizedProviderException() {
        ResolvedLlmConfig config = openAiConfig();
        OpenAiCompatibleLlmException source = OpenAiCompatibleLlmException.unauthorized(403);

        LlmProviderException translated = LlmExceptionTranslator.translate(source, config, "chat", "gpt-4o");

        assertEquals(LlmFailureKind.UNAUTHORIZED, translated.failureKind());
        assertEquals(ErrorCode.LLM_UNAUTHORIZED, translated.errorCode());
        assertEquals(HttpStatus.UNAUTHORIZED, translated.httpStatus());
        assertTrue(translated.publicMessage().contains("403"));
    }

    @Test
    void translate_timeout_mapsToLlmTimeoutException() {
        ResolvedLlmConfig config = openAiConfig();
        OpenAiCompatibleLlmException source =
                OpenAiCompatibleLlmException.timeout(new SocketTimeoutException("read timed out"));

        LlmProviderException translated = LlmExceptionTranslator.translate(source, config, "chat", "gpt-4o");

        assertInstanceOf(LlmTimeoutException.class, translated);
        assertEquals(ErrorCode.LLM_TIMEOUT, translated.errorCode());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, translated.httpStatus());
    }

    @Test
    void translate_endpointNotFound_mapsToProviderError() {
        ResolvedLlmConfig config = openAiConfig();
        OpenAiCompatibleLlmException source =
                OpenAiCompatibleLlmException.endpointNotFound("http://bad/v1/chat/completions", 404);

        LlmProviderException translated = LlmExceptionTranslator.translate(source, config, "chat", "gpt-4o");

        assertEquals(LlmFailureKind.ENDPOINT_NOT_FOUND, translated.failureKind());
        assertEquals(ErrorCode.LLM_PROVIDER_ERROR, translated.errorCode());
        assertTrue(translated.publicMessage().contains("endpoint not found"));
    }

    @Test
    void translate_clientResolution_mapsToConfigurationException() {
        ResolvedLlmConfig config = openAiConfig();
        LlmClientResolutionException source = new LlmClientResolutionException("provider missing");

        LlmProviderException translated = LlmExceptionTranslator.translate(source, config, "resolve", null);

        assertInstanceOf(LlmConfigurationException.class, translated);
        assertEquals("provider missing", translated.publicMessage());
    }

    @Test
    void translate_alreadyTyped_returnsSameInstance() {
        LlmConfigurationException source =
                LlmConfigurationException.invalidField(
                        LlmProvider.OPENAI_COMPATIBLE, "chat", "gpt-4o", "http://x", "bad config");

        LlmProviderException translated =
                LlmExceptionTranslator.translate(source, openAiConfig(), "chat", "gpt-4o");

        assertSame(source, translated);
    }

    private static void assertSame(LlmConfigurationException expected, LlmProviderException actual) {
        assertEquals(expected, actual);
    }

    private static ResolvedLlmConfig openAiConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "gpt-4o",
                "embed-model",
                "LITELLM_API_KEY",
                null,
                0.2,
                30_000,
                null,
                Map.of());
    }
}
