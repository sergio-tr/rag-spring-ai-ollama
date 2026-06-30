package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.domain.exception.ErrorCode;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.health.RagHealthProperties;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaModelProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OllamaConnectivityCheckerTest {

    private RagHealthProperties healthProperties;
    private LlmProperties llmProperties;
    private OllamaApiClient ollamaApiClient;
    private OllamaModelProvisioningService provisioningService;
    private OllamaConnectivityChecker checker;

    @BeforeEach
    void setUp() {
        healthProperties = mock(RagHealthProperties.class);
        llmProperties = new LlmProperties();
        ollamaApiClient = mock(OllamaApiClient.class);
        provisioningService = mock(OllamaModelProvisioningService.class);
        checker = new OllamaConnectivityChecker(healthProperties, llmProperties, ollamaApiClient, provisioningService);
    }

    @Test
    void isOllamaReachable_whenOllamaDisabled_returnsTrueWithoutPing() throws Exception {
        when(healthProperties.isOllamaEnabled()).thenReturn(false);

        assertTrue(checker.isOllamaReachable());
        verify(ollamaApiClient, never()).ping();
    }

    @Test
    void isOllamaReachable_whenPingTrue_returnsTrue() throws Exception {
        when(healthProperties.isOllamaEnabled()).thenReturn(true);
        when(ollamaApiClient.ping()).thenReturn(true);

        assertTrue(checker.isOllamaReachable());
    }

    @Test
    void isOllamaReachable_whenPingFalse_returnsFalse() throws Exception {
        when(healthProperties.isOllamaEnabled()).thenReturn(true);
        when(ollamaApiClient.ping()).thenReturn(false);

        assertFalse(checker.isOllamaReachable());
    }

    @Test
    void isOllamaReachable_whenInterrupted_returnsFalse() throws Exception {
        when(healthProperties.isOllamaEnabled()).thenReturn(true);
        when(ollamaApiClient.ping()).thenThrow(new InterruptedException("stop"));

        assertFalse(checker.isOllamaReachable());
        assertTrue(Thread.interrupted());
    }

    @Test
    void isOllamaReachable_whenGenericException_returnsFalse() throws Exception {
        when(healthProperties.isOllamaEnabled()).thenReturn(true);
        when(ollamaApiClient.ping()).thenThrow(new RuntimeException("net"));

        assertFalse(checker.isOllamaReachable());
    }

    @Test
    void prepareForQuery_whenOllamaDisabled_doesNothing() throws Exception {
        when(healthProperties.isOllamaEnabled()).thenReturn(false);

        checker.prepareForQuery("m");

        verify(ollamaApiClient, never()).ping();
        verify(provisioningService, never()).ensureChatAndEmbeddingModelsPresent(any());
    }

    @Test
    void prepareForQuery_openAiOnlyProviders_skipsOllamaChecks() throws Exception {
        llmProperties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        when(healthProperties.isOllamaEnabled()).thenReturn(true);

        checker.prepareForQuery("m");

        verify(ollamaApiClient, never()).ping();
        verify(provisioningService, never()).ensureChatAndEmbeddingModelsPresent(any());
    }

    @Test
    void prepareForQuery_whenPingFalse_throwsLlmUnavailable() throws Exception {
        when(healthProperties.isOllamaEnabled()).thenReturn(true);
        when(ollamaApiClient.ping()).thenReturn(false);

        RagServiceException ex = assertThrows(RagServiceException.class, () -> checker.prepareForQuery(null));
        assertEquals(ErrorCode.LLM_UNAVAILABLE, ex.getErrorCode());
        verify(provisioningService, never()).ensureChatAndEmbeddingModelsPresent(any());
    }

    @Test
    void prepareForQuery_whenPingThrowsIOException_wrapsAsLlmUnavailable() throws Exception {
        when(healthProperties.isOllamaEnabled()).thenReturn(true);
        when(ollamaApiClient.ping()).thenThrow(new IOException("reset"));

        RagServiceException ex = assertThrows(RagServiceException.class, () -> checker.prepareForQuery(null));
        assertEquals(ErrorCode.LLM_UNAVAILABLE, ex.getErrorCode());
        assertInstanceOf(IOException.class, ex.getCause());
    }

    @Test
    void prepareForQuery_whenInterrupted_throwsLlmUnavailable() throws Exception {
        when(healthProperties.isOllamaEnabled()).thenReturn(true);
        when(ollamaApiClient.ping()).thenThrow(new InterruptedException("wait"));

        RagServiceException ex = assertThrows(RagServiceException.class, () -> checker.prepareForQuery("x"));
        assertEquals(ErrorCode.LLM_UNAVAILABLE, ex.getErrorCode());
        assertInstanceOf(InterruptedException.class, ex.getCause());
        assertTrue(Thread.interrupted());
    }

    @Test
    void prepareForQuery_whenPingOk_invokesProvisioningWithOverride() throws Exception {
        when(healthProperties.isOllamaEnabled()).thenReturn(true);
        when(ollamaApiClient.ping()).thenReturn(true);

        checker.prepareForQuery("lab:chat");

        verify(provisioningService).ensureChatAndEmbeddingModelsPresent("lab:chat");
    }
}
