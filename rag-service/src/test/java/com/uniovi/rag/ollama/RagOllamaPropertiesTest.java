package com.uniovi.rag.ollama;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RagOllamaPropertiesTest {

    @Test
    void defaults() {
        RagOllamaProperties p = new RagOllamaProperties();
        assertTrue(p.isAutoPullEnabled());
        assertEquals(1_800_000L, p.getPullReadTimeoutMs());
    }

    @Test
    void setters() {
        RagOllamaProperties p = new RagOllamaProperties();
        p.setAutoPullEnabled(false);
        p.setPullReadTimeoutMs(99_000L);
        assertFalse(p.isAutoPullEnabled());
        assertEquals(99_000L, p.getPullReadTimeoutMs());
    }
}
