package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExplanationTest {

    @Test
    void gettersAndIdentifier() {
        long ts = System.currentTimeMillis();
        Explanation e = new Explanation("min-1", "2025-01-15", "Sala A", "Content", 0.85, ts);
        assertEquals("min-1", e.getMinuteId());
        assertEquals("2025-01-15", e.getDate());
        assertEquals("Content", e.getContent());
        assertEquals(0.85, e.getRelevanceScore());
        assertEquals(ts, e.getTimestamp());
        assertTrue(e.getIdentifier().contains("min-1"));
        assertTrue(e.getAge() >= 0);
    }
}
