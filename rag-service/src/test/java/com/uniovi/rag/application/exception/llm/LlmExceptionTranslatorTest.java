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
        return new ResolvedLlmConfig(
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
