package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.interfaces.rest.support.dto.ApiResponse;
import com.uniovi.rag.application.exception.RagServiceException;
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
        ResponseEntity<ApiResponse<Void>> res = handler.handleRagService(ex);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, res.getStatusCode());
        assertNotNull(res.getBody());
        assertFalse(res.getBody().success());
        assertEquals("LLM_UNAVAILABLE", res.getBody().error().code());
        assertNotNull(res.getBody().error().message());
    }
}
