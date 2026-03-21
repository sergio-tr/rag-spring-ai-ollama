package com.uniovi.rag.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RagHealthPropertiesTest {

    @Test
    void defaults() {
        RagHealthProperties p = new RagHealthProperties();
        assertTrue(p.isOllamaEnabled());
        assertTrue(p.isClassifierEnabled());
        assertTrue(p.isOllamaVerifyModels());
        assertTrue(p.isClassifierRequireModelLoaded());
        assertEquals(3000, p.getConnectTimeoutMs());
        assertEquals(3000, p.getReadTimeoutMs());
    }

    @Test
    void setters() {
        RagHealthProperties p = new RagHealthProperties();
        p.setOllamaEnabled(false);
        p.setClassifierEnabled(false);
        p.setOllamaVerifyModels(false);
        p.setClassifierRequireModelLoaded(false);
        p.setConnectTimeoutMs(100);
        p.setReadTimeoutMs(200);
        assertFalse(p.isOllamaEnabled());
        assertFalse(p.isClassifierEnabled());
        assertFalse(p.isOllamaVerifyModels());
        assertFalse(p.isClassifierRequireModelLoaded());
        assertEquals(100, p.getConnectTimeoutMs());
        assertEquals(200, p.getReadTimeoutMs());
    }
}
