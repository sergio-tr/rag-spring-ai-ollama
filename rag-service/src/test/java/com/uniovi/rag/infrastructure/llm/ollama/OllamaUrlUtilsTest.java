package com.uniovi.rag.infrastructure.llm.ollama;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OllamaUrlUtilsTest {

    @Test
    void stripTrailingSlash_nullOrEmpty_returnsDefault() {
        assertEquals("http://localhost:11434", OllamaUrlUtils.stripTrailingSlash(null));
        assertEquals("http://localhost:11434", OllamaUrlUtils.stripTrailingSlash(""));
    }

    @Test
    void stripTrailingSlash_removesSlash() {
        assertEquals("http://host:11434", OllamaUrlUtils.stripTrailingSlash("http://host:11434/"));
    }

    @Test
    void stripTrailingSlash_noSlash_unchanged() {
        assertEquals("http://host:11434", OllamaUrlUtils.stripTrailingSlash("http://host:11434"));
    }
}
