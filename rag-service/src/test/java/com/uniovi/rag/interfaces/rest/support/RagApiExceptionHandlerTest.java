package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.interfaces.rest.support.dto.ApiErrorResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.*;

class RagApiExceptionHandlerTest {

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
    }
}
