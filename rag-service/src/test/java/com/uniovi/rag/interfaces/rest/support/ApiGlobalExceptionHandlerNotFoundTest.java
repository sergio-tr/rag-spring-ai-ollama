package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.interfaces.rest.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class ApiGlobalExceptionHandlerNotFoundTest {

    @Test
    void handleNotFound_returnsJson404NotHtml() {
        ApiGlobalExceptionHandler handler = new ApiGlobalExceptionHandler();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v5/conversations/00000000-0000-4000-8000-000000000002/messages");

        var res = handler.handleNotFound(new NotFoundException("conversation not found"), req);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(404, res.getBody().status());
        assertEquals("NOT_FOUND", res.getBody().code());
        assertFalse(res.getBody().success());
        assertFalse(res.getBody().message().toLowerCase().contains("<html"));
    }
}
