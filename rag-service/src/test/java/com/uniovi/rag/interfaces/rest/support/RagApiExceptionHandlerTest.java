package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.exception.llm.LlmConfigurationException;
import com.uniovi.rag.application.exception.llm.LlmRemoteFailures;
import com.uniovi.rag.application.exception.llm.LlmTimeoutException;
import com.uniovi.rag.domain.exception.ErrorCode;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.interfaces.rest.support.dto.ApiErrorResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.*;

class RagApiExceptionHandlerTest {

    @Test
    void handleLlmProvider_unauthorized_maps401AndEnvelope() {
        RagApiExceptionHandler handler = new RagApiExceptionHandler();
        var ex =
                LlmRemoteFailures.unauthorized(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "chat",
                        "gpt-4o",
                        "http://litellm:4000",
                        401);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v5/conversations/x/messages");
        var res = handler.handleLlmProvider(ex, req);
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(ErrorCode.LLM_UNAUTHORIZED.name(), res.getBody().code());
        assertFalse(res.getBody().message().toLowerCase().contains("bearer"));
        assertFalse(res.getBody().message().contains("sk-"));
    }

    @Test
    void handleLlmProvider_timeout_maps504() {
        RagApiExceptionHandler handler = new RagApiExceptionHandler();
        var ex =
                new LlmTimeoutException(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "chat",
                        "gpt-4o",
                        "http://litellm:4000",
                        30_000,
                        null);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v5/conversations/x/messages");
        var res = handler.handleLlmProvider(ex, req);
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, res.getStatusCode());
        assertEquals(ErrorCode.LLM_TIMEOUT.name(), res.getBody().code());
    }

    @Test
    void handleLlmProvider_configuration_maps422() {
        RagApiExceptionHandler handler = new RagApiExceptionHandler();
        var ex =
                LlmConfigurationException.missingApiKeyEnv(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "chat",
                        "gpt-4o",
                        "http://litellm:4000",
                        "LITELLM_API_KEY");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v5/conversations/x/messages");
        var res = handler.handleLlmProvider(ex, req);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, res.getStatusCode());
        assertEquals(ErrorCode.LLM_MISCONFIGURED.name(), res.getBody().code());
        assertTrue(res.getBody().message().contains("LITELLM_API_KEY"));
    }

    @Test
    void handleRagService_mapsToStatusAndEnvelope() {
        RagApiExceptionHandler handler = new RagApiExceptionHandler();
        RagServiceException ex = RagServiceException.llmUnavailable(new ConnectException("refused"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v5/lab/status");
        ResponseEntity<ApiErrorResponse> res = handler.handleRagService(ex, req);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(503, res.getBody().status());
        assertEquals("LLM_UNAVAILABLE", res.getBody().code());
        assertNotNull(res.getBody().message());
        assertFalse(
                res.getBody().message().toLowerCase().contains("<html"),
                "API envelope must not echo HTML error pages");
    }

    @Test
    void handleRagService_chatDocumentScope_maps422AndCode() {
        RagApiExceptionHandler handler = new RagApiExceptionHandler();
        RagServiceException ex = RagServiceException.chatDocumentScopeEmpty();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v5/conversations/x/messages");
        var res = handler.handleRagService(ex, req);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(ErrorCode.CHAT_DOCUMENT_SCOPE_EMPTY.name(), res.getBody().code());
        assertFalse(res.getBody().success());
        assertEquals(ErrorCode.CHAT_DOCUMENT_SCOPE_EMPTY.name(), res.getBody().error().code());
        assertFalse(res.getBody().message().toLowerCase().contains("<html"));
    }

    @Test
    void handleRagService_knowledgeSnapshotUnavailable_maps422AndEnvelope() {
        RagApiExceptionHandler handler = new RagApiExceptionHandler();
        RagServiceException ex = RagServiceException.knowledgeSnapshotUnavailable();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v5/conversations/00000000-0000-4000-8000-000000000001/messages");
        var res = handler.handleRagService(ex, req);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(ErrorCode.KNOWLEDGE_SNAPSHOT_UNAVAILABLE.name(), res.getBody().code());
        assertFalse(res.getBody().success());
        assertFalse(res.getBody().message().toLowerCase().contains("<html"));
    }

    @Test
    void handleRagService_chatDocumentFilterInvalid_maps400AndEnvelope() {
        RagApiExceptionHandler handler = new RagApiExceptionHandler();
        RagServiceException ex = RagServiceException.chatDocumentFilterInvalid();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v5/conversations/uuid/messages");
        var res = handler.handleRagService(ex, req);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(ErrorCode.CHAT_DOCUMENT_FILTER_INVALID.name(), res.getBody().code());
        assertFalse(res.getBody().success());
        assertFalse(res.getBody().message().toLowerCase().contains("<html"));
    }
}
