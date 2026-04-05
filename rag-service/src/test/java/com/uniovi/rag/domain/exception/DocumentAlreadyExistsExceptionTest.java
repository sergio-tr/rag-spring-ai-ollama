package com.uniovi.rag.domain.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentAlreadyExistsExceptionTest {

    @Test
    void messageAndDocumentId() {
        DocumentAlreadyExistsException e = new DocumentAlreadyExistsException("doc-123");
        assertEquals("doc-123", e.getDocumentId());
        assertTrue(e.getMessage().contains("doc-123"));
        assertTrue(e.getMessage().contains("already exists"));
    }

    @Test
    void isRuntimeException() {
        assertThrows(DocumentAlreadyExistsException.class, () -> {
            throw new DocumentAlreadyExistsException("id");
        });
    }
}
