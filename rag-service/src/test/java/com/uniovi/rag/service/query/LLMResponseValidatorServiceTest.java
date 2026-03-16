package com.uniovi.rag.service.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LLMResponseValidatorServiceTest {

    private LLMResponseValidatorService validator;

    @BeforeEach
    void setUp() {
        validator = new LLMResponseValidatorService();
    }

    @Test
    void isValidResponse_null_returnsFalse() {
        assertFalse(validator.isValidResponse(null, "ctx"));
    }

    @Test
    void isValidResponse_empty_returnsFalse() {
        assertFalse(validator.isValidResponse("", "ctx"));
        assertFalse(validator.isValidResponse("   ", "ctx"));
    }

    @Test
    void isValidResponse_tooShort_returnsFalse() {
        assertFalse(validator.isValidResponse("x", "ctx"));
    }

    @Test
    void isValidResponse_valid_returnsTrue() {
        assertTrue(validator.isValidResponse("Valid response here.", "ctx"));
    }

    @Test
    void isValidResponse_errorLike_returnsFalse() {
        assertFalse(validator.isValidResponse("Error: something failed", "ctx"));
        assertFalse(validator.isValidResponse("Exception: at com.xxx", "ctx"));
        assertFalse(validator.isValidResponse("An error occurred", "ctx"));
    }

    @Test
    void cleanResponse_null_returnsEmpty() {
        assertEquals("", validator.cleanResponse(null));
    }

    @Test
    void cleanResponse_removesCodeBlocks() {
        String input = "```json\n{\"a\":1}\n```";
        String out = validator.cleanResponse(input);
        assertFalse(out.contains("```"));
    }

    @Test
    void cleanResponse_removesQuotes() {
        assertEquals("hello", validator.cleanResponse("\"hello\""));
        assertEquals("hello", validator.cleanResponse("'hello'"));
    }

    @Test
    void validateAndClean_valid_returnsCleaned() {
        String result = validator.validateAndClean("  Good answer  ", "ctx");
        assertNotNull(result);
        assertEquals("Good answer", result);
    }

    @Test
    void validateAndClean_invalid_returnsNull() {
        assertNull(validator.validateAndClean(null, "ctx"));
        assertNull(validator.validateAndClean("x", "ctx"));
    }
}
